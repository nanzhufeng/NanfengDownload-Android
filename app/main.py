from __future__ import annotations

import sys
import re
import time
import traceback
import urllib.request
from dataclasses import dataclass, replace
from pathlib import Path
from typing import Any

from PySide6.QtCore import QEvent, QObject, QRect, Qt, QThread, QTimer, Signal, Slot, QSemaphore
from PySide6.QtGui import QColor, QDesktopServices, QFont, QIcon
from PySide6.QtWidgets import (
    QApplication,
    QFileDialog,
    QFrame,
    QGridLayout,
    QHBoxLayout,
    QHeaderView,
    QLabel,
    QLineEdit,
    QMainWindow,
    QMessageBox,
    QPushButton,
    QComboBox,
    QPlainTextEdit,
    QProgressBar,
    QStyle,
    QStyledItemDelegate,
    QStyleOptionButton,
    QTableWidget,
    QTableWidgetItem,
    QVBoxLayout,
    QWidget,
)

from .auth_profile import AUTH_COOKIE_MODE, find_browser_path, launch_login_browser, open_login_browser
from .catalog import CatalogItem, discover_links
from .downloader import (
    DownloadOptions,
    DownloadStopped,
    detect_platform,
    download_url,
    find_ffmpeg_dir,
    safe_path_name,
    split_urls,
)


APP_NAME = "南烛枫 - 视频下载器"
QUALITY_OPTIONS = ["最佳画质", "1080p 及以下", "720p 及以下", "仅音频 MP3"]
COL_INDEX = 0
COL_SELECT = 1
COL_STATUS = 2
COL_PLATFORM = 3
COL_CREATOR = 4
COL_QUALITY = 5
COL_TITLE = 6
COL_PROGRESS = 7
COL_SPEED = 8
COL_ETA = 9
COL_LINK = 10
MEDIA_FILE_SUFFIXES = {".mp4", ".webm", ".mkv", ".mov", ".m4a", ".mp3"}
FULL_TEXT_TOOLTIP_COLUMNS = {COL_CREATOR, COL_TITLE, COL_LINK}


def default_output_dir() -> Path:
    d_drive = Path("D:/")
    if d_drive.exists():
        return d_drive / "南烛枫视频下载器"
    return Path.home() / "Downloads" / "南烛枫视频下载器"


NETWORK_ERROR_KEYWORDS = (
    "timed out",
    "timeout",
    "urlopen error",
    "ssl",
    "connection",
    "connection reset",
    "connection aborted",
    "remote end closed",
    "network",
    "temporary failure",
    "name resolution",
    "getaddrinfo",
    "winerror 100",
    "winerror 110",
    "errno 11001",
    "read operation timed out",
    "连接",
    "超时",
    "网络",
    "远程主机",
)


def is_network_error_text(text: str) -> bool:
    lowered = text.lower()
    return any(keyword in lowered for keyword in NETWORK_ERROR_KEYWORDS)


class CenteredCheckBoxDelegate(QStyledItemDelegate):
    @staticmethod
    def _check_state_value(check_state: Any) -> int:
        return int(getattr(check_state, "value", check_state))

    def paint(self, painter, option, index) -> None:
        check_state = index.data(Qt.CheckStateRole)
        if check_state is None:
            super().paint(painter, option, index)
            return

        style = option.widget.style() if option.widget else QApplication.style()
        style.drawPrimitive(QStyle.PE_PanelItemViewItem, option, painter, option.widget)

        indicator_width = style.pixelMetric(QStyle.PM_IndicatorWidth, None, option.widget)
        indicator_height = style.pixelMetric(QStyle.PM_IndicatorHeight, None, option.widget)
        indicator_rect = QRect(
            option.rect.center().x() - indicator_width // 2,
            option.rect.center().y() - indicator_height // 2,
            indicator_width,
            indicator_height,
        )

        checkbox_option = QStyleOptionButton()
        checkbox_option.rect = indicator_rect
        checkbox_option.state = QStyle.State_Enabled
        checked_value = self._check_state_value(check_state)
        checkbox_option.state |= (
            QStyle.State_On
            if checked_value == Qt.CheckState.Checked.value
            else QStyle.State_Off
        )
        style.drawPrimitive(QStyle.PE_IndicatorItemViewItemCheck, checkbox_option, painter, option.widget)


class CenterComboBox(QComboBox):
    def __init__(self) -> None:
        super().__init__()
        self._popup_open = False
        self.setEditable(True)
        self.lineEdit().setReadOnly(True)
        self.lineEdit().setAlignment(Qt.AlignCenter)
        self.lineEdit().setFocusPolicy(Qt.NoFocus)
        self.lineEdit().installEventFilter(self)

    def eventFilter(self, watched: QObject, event: QEvent) -> bool:
        if watched is self.lineEdit() and event.type() == QEvent.MouseButtonPress:
            self._toggle_popup()
            return True
        return super().eventFilter(watched, event)

    def mousePressEvent(self, event) -> None:
        self._toggle_popup()
        event.accept()

    def showPopup(self) -> None:
        self._popup_open = True
        super().showPopup()

    def hidePopup(self) -> None:
        self._popup_open = False
        super().hidePopup()

    def _toggle_popup(self) -> None:
        if self._popup_open or self.view().isVisible():
            self.hidePopup()
        else:
            self.showPopup()


@dataclass
class QueueItem:
    url: str
    platform: str
    creator_name: str | None
    quality: str
    row: int


class DownloadWorker(QObject):
    should_download_row = Signal(int)
    item_started = Signal(int)
    item_progress = Signal(int, dict)
    item_finished = Signal(int, str)
    item_skipped = Signal(int, str)
    item_failed = Signal(int, str)
    item_stopped = Signal(int)
    all_done = Signal()

    def __init__(self, items: list[QueueItem], options: DownloadOptions) -> None:
        super().__init__()
        self.items = items
        self.options = options
        self._cancel_requested = False
        self._check_semaphore = QSemaphore(0)
        self._row_should_download = True

    @Slot()
    def run(self) -> None:
        for item in self.items:
            if self._cancel_requested:
                break
            if not self._ask_should_download(item.row):
                self.item_stopped.emit(item.row)
                continue
            self.item_started.emit(item.row)
            try:
                result = download_url(
                    item.url,
                    replace(self.options, creator_name=item.creator_name, quality=item.quality),
                    lambda info, row=item.row: self.item_progress.emit(row, info),
                    self.is_cancel_requested,
                )
            except DownloadStopped:
                self.item_stopped.emit(item.row)
                break
            except Exception as exc:
                detail = "".join(traceback.format_exception_only(type(exc), exc)).strip()
                self.item_failed.emit(item.row, detail)
                if is_network_error_text(detail):
                    break
            else:
                if result.skipped:
                    display_name = result.message or "文件已存在，已跳过下载"
                    self.item_skipped.emit(item.row, display_name)
                    continue
                display_name = result.files[0].name if result.files else "下载完成"
                self.item_finished.emit(item.row, display_name)
        self.all_done.emit()

    def cancel(self) -> None:
        self._cancel_requested = True

    def is_cancel_requested(self) -> bool:
        return self._cancel_requested

    def _ask_should_download(self, row: int) -> bool:
        self._row_should_download = False
        self.should_download_row.emit(row)
        self._check_semaphore.acquire()
        return self._row_should_download

    @Slot(bool)
    def receive_row_check(self, should_download: bool) -> None:
        self._row_should_download = should_download
        self._check_semaphore.release()


class DiscoveryWorker(QObject):
    item_found = Signal(object)
    failed = Signal(str)
    all_done = Signal(int)

    def __init__(self, text: str, options: DownloadOptions, max_items: int = 500) -> None:
        super().__init__()
        self.text = text
        self.options = options
        self.max_items = max_items

    @Slot()
    def run(self) -> None:
        count = 0
        try:
            for item in discover_links(self.text, self.options, max_items=self.max_items):
                self.item_found.emit(item)
                count += 1
        except Exception as exc:
            detail = "".join(traceback.format_exception_only(type(exc), exc)).strip()
            self.failed.emit(detail)
        self.all_done.emit(count)


class NetworkCheckWorker(QObject):
    finished = Signal(bool)

    def __init__(self, urls: list[str]) -> None:
        super().__init__()
        self.urls = urls

    @Slot()
    def run(self) -> None:
        headers = {"User-Agent": "Mozilla/5.0"}
        for url in self.urls:
            try:
                request = urllib.request.Request(url, headers=headers)
                with urllib.request.urlopen(request, timeout=5):
                    self.finished.emit(True)
                    return
            except Exception:
                continue
        self.finished.emit(False)


class LoginWorker(QObject):
    finished = Signal()
    failed = Signal(str)

    def __init__(self, platform: str) -> None:
        super().__init__()
        self.platform = platform

    @Slot()
    def run(self) -> None:
        try:
            open_login_browser(self.platform)
        except Exception as exc:
            detail = "".join(traceback.format_exception_only(type(exc), exc)).strip()
            self.failed.emit(detail)
        self.finished.emit()


class MainWindow(QMainWindow):
    def __init__(self) -> None:
        super().__init__()
        self.project_root = Path(getattr(sys, "_MEIPASS", Path(__file__).resolve().parents[1]))
        self.ffmpeg_dir = find_ffmpeg_dir(self.project_root)
        self.worker_thread: QThread | None = None
        self.worker: DownloadWorker | None = None
        self.discovery_thread: QThread | None = None
        self.discovery_worker: DiscoveryWorker | None = None
        self.network_check_thread: QThread | None = None
        self.network_check_worker: NetworkCheckWorker | None = None
        self.active_download_rows: set[int] = set()
        self.discovery_had_error = False
        self.discovery_source_text = ""
        self.discovery_limit = 500
        self.discovery_fallback_to_queue = False
        self.waiting_for_network = False
        self.check_drag_active = False
        self.check_drag_state = Qt.Unchecked
        self.check_drag_rows: set[int] = set()
        self.download_started_at: float | None = None
        self.network_check_timer = QTimer(self)
        self.network_check_timer.setInterval(8000)
        self.network_check_timer.timeout.connect(self._start_network_check)

        self.setWindowTitle(APP_NAME)
        self.setWindowIcon(QIcon(str(self.project_root / "app" / "assets" / "nanzhufeng-icon.png")))
        self.resize(1820, 1130)
        self._build_ui()
        self._apply_style()
        self._update_cookie_file_state()
        self._update_status()

    def _build_ui(self) -> None:
        root = QWidget()
        self.setCentralWidget(root)
        shell = QHBoxLayout(root)
        shell.setContentsMargins(24, 24, 24, 24)
        shell.setSpacing(18)

        sidebar = QFrame()
        sidebar.setObjectName("Sidebar")
        sidebar.setFixedWidth(220)
        sidebar_layout = QVBoxLayout(sidebar)
        sidebar_layout.setContentsMargins(20, 20, 20, 20)
        sidebar_layout.setSpacing(16)

        brand_mark = QLabel("南")
        brand_mark.setObjectName("BrandMark")
        brand_title = QLabel(APP_NAME)
        brand_title.setObjectName("BrandTitle")
        brand_subtitle = QLabel("批量视频下载工作台")
        brand_subtitle.setObjectName("BrandSubtitle")
        sidebar_layout.addWidget(brand_mark)
        sidebar_layout.addWidget(brand_title)
        sidebar_layout.addWidget(brand_subtitle)
        sidebar_layout.addSpacing(10)

        platform_title = QLabel("支持平台")
        platform_title.setObjectName("SideSectionTitle")
        platform_douyin = QLabel("抖音\n作者 / 作品")
        platform_douyin.setObjectName("PlatformDouyin")
        platform_youtube = QLabel("YouTube\n频道 / 播放列表")
        platform_youtube.setObjectName("PlatformYoutube")
        sidebar_layout.addWidget(platform_title)
        sidebar_layout.addWidget(platform_douyin)
        sidebar_layout.addWidget(platform_youtube)
        sidebar_layout.addSpacing(8)

        workflow_title = QLabel("操作流程")
        workflow_title.setObjectName("SideSectionTitle")
        sidebar_layout.addWidget(workflow_title)
        for index, text in enumerate(["登录账号", "粘贴链接", "读取列表", "勾选下载"], start=1):
            step_label = QLabel(f"{index:02d}  {text}")
            step_label.setObjectName("WorkflowStep")
            sidebar_layout.addWidget(step_label)
        sidebar_layout.addStretch(1)

        self.open_folder_button = QPushButton("打开保存目录")
        self.open_folder_button.setObjectName("SideButton")
        self.open_folder_button.clicked.connect(self._open_output_dir)
        sidebar_layout.addWidget(self.open_folder_button)
        shell.addWidget(sidebar)

        content = QWidget()
        layout = QVBoxLayout(content)
        layout.setContentsMargins(0, 0, 0, 0)
        layout.setSpacing(14)
        shell.addWidget(content, 1)

        top_bar = QHBoxLayout()
        title_block = QVBoxLayout()
        title_block.setSpacing(4)
        title = QLabel("下载控制台")
        title.setObjectName("Title")
        subtitle = QLabel("粘贴链接，读取作品列表，勾选后开始下载。")
        subtitle.setObjectName("Subtitle")
        title_block.addWidget(title)
        title_block.addWidget(subtitle)
        top_bar.addLayout(title_block)
        top_bar.addStretch(1)
        layout.addLayout(top_bar)

        input_frame = QFrame()
        input_frame.setObjectName("Panel")
        input_layout = QGridLayout(input_frame)
        input_layout.setContentsMargins(20, 18, 20, 18)
        input_layout.setHorizontalSpacing(12)
        input_layout.setVerticalSpacing(12)

        login_label = QLabel("登录")
        login_label.setObjectName("FieldLabel")
        self.login_douyin_button = QPushButton("登录抖音")
        self.login_douyin_button.setObjectName("LoginDouyinButton")
        self.login_douyin_button.setFixedWidth(150)
        self.login_douyin_button.clicked.connect(lambda: self._open_login_window("douyin"))
        self.login_youtube_button = QPushButton("登录 YouTube")
        self.login_youtube_button.setObjectName("LoginYoutubeButton")
        self.login_youtube_button.setFixedWidth(150)
        self.login_youtube_button.clicked.connect(lambda: self._open_login_window("youtube"))

        output_label = QLabel("保存位置")
        output_label.setObjectName("FieldLabel")
        self.output_edit = QLineEdit(str(default_output_dir()))
        self.output_button = QPushButton("选择")
        self.output_button.setObjectName("ChooseButton")
        self.output_button.clicked.connect(self._choose_output_dir)

        quality_label = QLabel("分辨率")
        quality_label.setObjectName("FieldLabel")
        self.quality_combo = CenterComboBox()
        self.quality_combo.addItems(QUALITY_OPTIONS)
        self.quality_combo.setCurrentText("720p 及以下")

        url_label = QLabel("链接")
        url_label.setObjectName("FieldLabel")
        self.url_text = QPlainTextEdit()
        self.url_text.setPlaceholderText("粘贴抖音链接、YouTube 链接，或 YouTube @频道名，可一次多行。")
        self.url_text.setFixedHeight(112)

        self.import_button = QPushButton("智能读取")
        self.import_button.setObjectName("SmartImportButton")
        self.import_button.clicked.connect(self._smart_import)
        url_button_panel = QFrame()
        url_button_panel.setObjectName("UrlButtonPanel")
        url_button_layout = QVBoxLayout(url_button_panel)
        url_button_layout.setContentsMargins(0, 0, 0, 0)
        url_button_layout.setSpacing(10)
        url_button_layout.addStretch(1)
        url_button_layout.addWidget(self.import_button)
        url_button_layout.addStretch(1)
        self.paste_hint = QLabel("支持公开内容和登录后有权限访问的所有内容。")
        self.paste_hint.setObjectName("Hint")

        input_layout.addWidget(login_label, 0, 0)
        input_layout.addWidget(self.login_douyin_button, 0, 1)
        input_layout.addWidget(self.login_youtube_button, 0, 2)
        input_layout.addWidget(output_label, 1, 0)
        input_layout.addWidget(self.output_edit, 1, 1, 1, 3)
        input_layout.addWidget(self.output_button, 1, 4)
        input_layout.addWidget(quality_label, 2, 0)
        input_layout.addWidget(self.quality_combo, 2, 1)
        input_layout.addWidget(url_label, 3, 0, Qt.AlignTop)
        input_layout.addWidget(self.url_text, 3, 1, 1, 3)
        input_layout.addWidget(url_button_panel, 3, 4, 1, 2)
        input_layout.addWidget(self.paste_hint, 4, 1, 1, 5)
        input_layout.setColumnStretch(1, 1)
        input_layout.setColumnStretch(3, 1)
        layout.addWidget(input_frame)

        action_frame = QFrame()
        action_frame.setObjectName("ActionPanel")
        action_layout = QHBoxLayout(action_frame)
        action_layout.setContentsMargins(14, 12, 14, 12)
        action_layout.setSpacing(10)
        self.start_button = QPushButton("开始下载")
        self.start_button.setObjectName("PrimaryButton")
        self.start_button.clicked.connect(self._start_downloads)
        self.stop_button = QPushButton("停止")
        self.stop_button.setObjectName("StopButton")
        self.stop_button.clicked.connect(self._stop_downloads)
        self.stop_button.setEnabled(False)
        self.clear_button = QPushButton("清空队列")
        self.clear_button.setObjectName("ClearButton")
        self.clear_button.clicked.connect(self._clear_queue)
        self.select_all_button = QPushButton("全选")
        self.select_all_button.setObjectName("SelectButton")
        self.select_all_button.clicked.connect(self._select_all_rows)
        self.invert_button = QPushButton("反选")
        self.invert_button.setObjectName("InvertButton")
        self.invert_button.clicked.connect(self._invert_rows)
        action_layout.addWidget(self.start_button)
        action_layout.addWidget(self.stop_button)
        action_layout.addWidget(self.clear_button)
        action_layout.addSpacing(12)
        action_layout.addWidget(self.select_all_button)
        action_layout.addWidget(self.invert_button)
        action_layout.addStretch(1)
        layout.addWidget(action_frame)

        self.table = QTableWidget(0, 11)
        self.table.setHorizontalHeaderLabels(["序号", "选择", "状态", "平台", "博主", "分辨率", "标题 / 文件", "进度", "速度", "剩余", "链接"])
        self.table.verticalHeader().setVisible(False)
        self.table.setSelectionBehavior(QTableWidget.SelectRows)
        self.table.setAlternatingRowColors(True)
        self.table.itemClicked.connect(self._on_table_item_clicked)
        self.table.viewport().installEventFilter(self)
        self.table.setItemDelegateForColumn(COL_SELECT, CenteredCheckBoxDelegate(self.table))
        header = self.table.horizontalHeader()
        header.setDefaultAlignment(Qt.AlignCenter)
        header.setSectionResizeMode(COL_INDEX, QHeaderView.Fixed)
        self.table.setColumnWidth(COL_INDEX, 54)
        header.setSectionResizeMode(COL_SELECT, QHeaderView.Fixed)
        self.table.setColumnWidth(COL_SELECT, 58)
        header.setSectionResizeMode(COL_STATUS, QHeaderView.Fixed)
        self.table.setColumnWidth(COL_STATUS, 84)
        header.setSectionResizeMode(COL_PLATFORM, QHeaderView.Fixed)
        self.table.setColumnWidth(COL_PLATFORM, 86)
        header.setSectionResizeMode(COL_CREATOR, QHeaderView.Fixed)
        self.table.setColumnWidth(COL_CREATOR, 132)
        header.setSectionResizeMode(COL_QUALITY, QHeaderView.Fixed)
        self.table.setColumnWidth(COL_QUALITY, 142)
        header.setSectionResizeMode(COL_TITLE, QHeaderView.Stretch)
        header.setSectionResizeMode(COL_PROGRESS, QHeaderView.Fixed)
        self.table.setColumnWidth(COL_PROGRESS, 86)
        header.setSectionResizeMode(COL_SPEED, QHeaderView.Fixed)
        self.table.setColumnWidth(COL_SPEED, 102)
        header.setSectionResizeMode(COL_ETA, QHeaderView.Fixed)
        self.table.setColumnWidth(COL_ETA, 96)
        header.setSectionResizeMode(COL_LINK, QHeaderView.Stretch)
        layout.addWidget(self.table, 1)

        bottom_bar = QHBoxLayout()
        self.status_label = QLabel()
        self.status_label.setObjectName("Status")
        self.copy_tip_label = QLabel()
        self.copy_tip_label.setObjectName("CopyToast")
        self.copy_tip_label.setFixedSize(150, 30)
        self.copy_tip_label.setProperty("active", "false")
        self.load_more_button = QPushButton("加载更多视频")
        self.load_more_button.setObjectName("LoadMoreButton")
        self.load_more_button.clicked.connect(self._load_more_discovery)
        self.load_more_button.setVisible(False)
        self.main_progress = QProgressBar()
        self.main_progress.setFixedWidth(260)
        self.main_progress.setRange(0, 100)
        self.main_progress.setValue(0)
        self.total_eta_label = QLabel("总剩余：--")
        self.total_eta_label.setObjectName("TotalEta")
        self.total_eta_label.setFixedWidth(110)
        self.total_eta_label.setAlignment(Qt.AlignRight | Qt.AlignVCenter)
        bottom_bar.addWidget(self.status_label)
        bottom_bar.addWidget(self.copy_tip_label)
        bottom_bar.addWidget(self.load_more_button)
        bottom_bar.addStretch(1)
        bottom_bar.addWidget(self.total_eta_label)
        bottom_bar.addWidget(self.main_progress)
        layout.addLayout(bottom_bar)

    def _apply_style(self) -> None:
        app_font = QFont("Microsoft YaHei UI", 10)
        QApplication.instance().setFont(app_font)
        chevron_icon = (Path(__file__).resolve().parent / "assets" / "chevron-down.svg").as_posix()
        stylesheet = """
            QMainWindow {
                background: #f3f5fb;
                font-family: "Microsoft YaHei UI", "Microsoft YaHei", "SimSun", "Segoe UI";
            }
            QMenuBar {
                background: #f3f5fb;
                color: #667085;
                border: none;
            }
            QLabel {
                color: #202939;
            }
            QLabel#Title {
                font-size: 25px;
                font-weight: 700;
                color: #111827;
            }
            QLabel#Subtitle, QLabel#Hint, QLabel#Status {
                color: #7b8496;
            }
            QLabel#TotalEta {
                color: #566176;
                font-weight: 700;
            }
            QLabel#CopyToast {
                background: transparent;
                color: transparent;
                border-radius: 8px;
                padding: 6px 12px;
                font-weight: 800;
            }
            QLabel#CopyToast[active="true"] {
                background: #16a34a;
                color: #ffffff;
                border-radius: 8px;
                padding: 6px 12px;
                font-weight: 800;
            }
            QLabel#FieldLabel {
                color: #384152;
                font-weight: 600;
            }
            QFrame#Sidebar {
                background: #ffffff;
                border: 1px solid #e7ebf4;
                border-radius: 8px;
            }
            QLabel#BrandMark {
                background: #fff1f5;
                color: #fb5d84;
                border: 1px solid #ffd4df;
                border-radius: 8px;
                min-width: 34px;
                max-width: 34px;
                min-height: 34px;
                max-height: 34px;
                qproperty-alignment: AlignCenter;
                font-size: 18px;
                font-weight: 800;
            }
            QLabel#BrandTitle {
                color: #111827;
                font-size: 16px;
                font-weight: 800;
            }
            QLabel#BrandSubtitle {
                color: #8a93a6;
                font-size: 12px;
            }
            QLabel#SideSectionTitle {
                color: #8a93a6;
                font-size: 12px;
                font-weight: 700;
                padding: 4px 2px;
            }
            QLabel#PlatformDouyin, QLabel#PlatformYoutube {
                border-radius: 8px;
                padding: 12px;
                font-weight: 800;
                line-height: 150%;
            }
            QLabel#PlatformDouyin {
                background: #fff1f5;
                color: #dc3d65;
                border: 1px solid #ffd2df;
            }
            QLabel#PlatformYoutube {
                background: #eef3ff;
                color: #3461ff;
                border: 1px solid #d6dfff;
            }
            QLabel#WorkflowStep {
                background: #f8faff;
                color: #566176;
                border: 1px solid #e6ebf5;
                border-radius: 8px;
                padding: 9px 11px;
                font-weight: 700;
            }
            QFrame#Panel {
                background: #ffffff;
                border: 1px solid #e5eaf3;
                border-radius: 8px;
            }
            QFrame#ActionPanel {
                background: #ffffff;
                border: 1px solid #e5eaf3;
                border-radius: 8px;
            }
            QPlainTextEdit, QLineEdit, QComboBox {
                background: #fbfcff;
                border: 1px solid #dbe3f0;
                border-radius: 6px;
                padding: 9px;
                color: #111827;
                selection-background-color: #6c7cff;
            }
            QPlainTextEdit:focus, QLineEdit:focus, QComboBox:focus {
                border: 1px solid #6c7cff;
                background: #ffffff;
            }
            QComboBox {
                padding-right: 34px;
            }
            QComboBox::drop-down {
                subcontrol-origin: padding;
                subcontrol-position: top right;
                width: 30px;
                border-left: 1px solid #e3e8f1;
                border-top-right-radius: 6px;
                border-bottom-right-radius: 6px;
                background: #f6f8fc;
            }
            QComboBox::down-arrow {
                image: url(__CHEVRON_ICON__);
                width: 14px;
                height: 14px;
                margin-right: 8px;
            }
            QComboBox::drop-down:hover {
                background: #eef3ff;
            }
            QPushButton {
                background: #f6f8fc;
                color: #364153;
                border: 1px solid #dbe3f0;
                border-radius: 6px;
                padding: 9px 14px;
                min-width: 82px;
            }
            QPushButton:hover {
                background: #eef3ff;
                border: 1px solid #cbd6ff;
            }
            QPushButton:disabled {
                color: #a2abbb;
                background: #f3f5f9;
                border: 1px solid #e3e8f1;
            }
            QPushButton#PrimaryButton {
                background: qlineargradient(x1:0, y1:0, x2:1, y2:0, stop:0 #3461ff, stop:1 #ff6f91);
                border: 1px solid #4f6dff;
                color: #ffffff;
                font-weight: 700;
            }
            QPushButton#PrimaryButton:hover {
                background: qlineargradient(x1:0, y1:0, x2:1, y2:0, stop:0 #254ff0, stop:1 #fb5d84);
            }
            QPushButton#SideButton {
                background: #3461ff;
                border: 1px solid #3461ff;
                color: #ffffff;
                font-weight: 700;
            }
            QPushButton#LoginDouyinButton {
                background: #fff1f5;
                border: 1px solid #ffd2df;
                color: #dc3d65;
                font-weight: 700;
            }
            QPushButton#LoginYoutubeButton {
                background: #eef3ff;
                border: 1px solid #d6dfff;
                color: #3461ff;
                font-weight: 700;
            }
            QPushButton#ChooseButton {
                background: #eefbf6;
                border: 1px solid #c8f0df;
                color: #138a5e;
                font-weight: 700;
            }
            QPushButton#SmartImportButton {
                background: #fff7ed;
                border: 1px solid #fed7aa;
                color: #c15f0a;
                font-weight: 700;
            }
            QPushButton#StopButton {
                background: #fff1f3;
                border: 1px solid #ffcdd6;
                color: #d92d50;
                font-weight: 700;
            }
            QPushButton#ClearButton {
                background: #f8faff;
                border: 1px solid #dce5f5;
                color: #4b5567;
                font-weight: 700;
            }
            QPushButton#SelectButton {
                background: #edfdfb;
                border: 1px solid #c5f0ea;
                color: #0f766e;
                font-weight: 700;
            }
            QPushButton#InvertButton {
                background: #fef6ff;
                border: 1px solid #f3d1fb;
                color: #9f2bb8;
                font-weight: 700;
            }
            QPushButton#LoadMoreButton {
                background: #eef3ff;
                border: 1px solid #cbd6ff;
                color: #3461ff;
                font-weight: 700;
                min-width: 118px;
            }
            QComboBox#TableCombo {
                min-width: 104px;
                padding: 4px 6px;
            }
            QComboBox#TableCombo::drop-down {
                width: 0px;
                border: none;
                background: transparent;
            }
            QComboBox#TableCombo::down-arrow {
                image: none;
                width: 0px;
                height: 0px;
            }
            QComboBox#TableCombo QLineEdit {
                border: none;
                background: transparent;
                padding: 0;
                selection-background-color: transparent;
            }
            QPushButton#LoginDouyinButton:disabled,
            QPushButton#LoginYoutubeButton:disabled,
            QPushButton#ChooseButton:disabled,
            QPushButton#SmartImportButton:disabled,
            QPushButton#StopButton:disabled,
            QPushButton#ClearButton:disabled,
            QPushButton#SelectButton:disabled,
            QPushButton#InvertButton:disabled,
            QPushButton#LoadMoreButton:disabled {
                color: #a2abbb;
                background: #f3f5f9;
                border: 1px solid #e3e8f1;
            }
            QTableWidget {
                background: #ffffff;
                alternate-background-color: #f8faff;
                border: 1px solid #e5eaf3;
                border-radius: 8px;
                gridline-color: #edf1f7;
                selection-background-color: #eef3ff;
                selection-color: #111827;
            }
            QHeaderView::section {
                background: #f3f6fb;
                color: #4b5567;
                padding: 10px 8px;
                border: none;
                border-right: 1px solid #e5eaf3;
                font-weight: 700;
            }
            QProgressBar {
                background: #edf2fb;
                border: 1px solid #dbe3f0;
                border-radius: 6px;
                height: 16px;
                text-align: center;
                color: #273244;
            }
            QProgressBar::chunk {
                background: #ff8a4c;
                border-radius: 5px;
            }
            """
        self.setStyleSheet(stylesheet.replace("__CHEVRON_ICON__", chevron_icon))

    def _update_status(self) -> None:
        self.status_label.setText(f"队列: {self.table.rowCount()} 项")

    def _update_cookie_file_state(self) -> None:
        browser_available = find_browser_path() is not None
        self.login_douyin_button.setEnabled(browser_available)
        self.login_youtube_button.setEnabled(browser_available)
        if not browser_available:
            self.status_label.setText("未找到 Chrome 或 Edge：软件可打开，但软件内登录窗口不可用。")

    def _choose_output_dir(self) -> None:
        directory = QFileDialog.getExistingDirectory(self, "选择保存位置", self.output_edit.text())
        if directory:
            self.output_edit.setText(directory)

    def _open_login_window(self, platform: str) -> None:
        try:
            launch_login_browser(platform)
        except Exception as exc:
            detail = "".join(traceback.format_exception_only(type(exc), exc)).strip()
            self._on_login_failed(detail)
            return
        platform_name = "抖音" if platform == "douyin" else "YouTube"
        self.status_label.setText(f"已打开独立{platform_name}登录窗口。主软件关闭后，该窗口仍会保留。")

    @Slot(str)
    def _on_login_failed(self, error: str) -> None:
        QMessageBox.warning(self, "打开登录窗口失败", error[:500])

    @Slot(str)
    def _on_login_finished(self, platform: str) -> None:
        platform_name = "抖音" if platform == "douyin" else "YouTube"
        self.status_label.setText(f"{platform_name}登录窗口已关闭，登录态已保存。")

    @Slot(str)
    def _cleanup_login_worker(self, platform: str) -> None:
        self._update_cookie_file_state()

    def _open_output_dir(self) -> None:
        path = Path(self.output_edit.text()).expanduser()
        path.mkdir(parents=True, exist_ok=True)
        QDesktopServices.openUrl(path.as_uri())

    def _smart_import(self) -> None:
        self._start_discovery(fallback_to_queue=True)

    def _add_urls(self, text: str | None = None, show_message: bool = True) -> int:
        urls = split_urls(text if text is not None else self.url_text.toPlainText())
        if not urls:
            if show_message:
                QMessageBox.information(self, "没有发现链接", "请先粘贴抖音链接、YouTube 链接，或 YouTube @频道名。")
            return 0

        existing = {
            self.table.item(row, COL_LINK).text()
            for row in range(self.table.rowCount())
            if self.table.item(row, COL_LINK)
        }
        added = 0
        for url in urls:
            if url in existing:
                continue
            self._add_queue_row(url=url, platform=detect_platform(url), title="等待解析")
            added += 1

        self._update_status()
        if added == 0 and show_message:
            QMessageBox.information(self, "没有新增", "这些链接已经在队列里。")
        return added

    def _start_discovery(self, load_more: bool = False, fallback_to_queue: bool = False) -> None:
        if self.worker_thread or self.discovery_thread:
            return
        if not load_more:
            self.discovery_limit = 500
        text = self.discovery_source_text if load_more and self.discovery_source_text else self.url_text.toPlainText().strip()
        if not split_urls(text):
            QMessageBox.information(self, "没有发现链接", "请先粘贴抖音作者页、作品页、YouTube 频道链接、播放列表链接，或 YouTube @频道名。")
            return

        options = self._build_options()
        if not options:
            return

        self.discovery_thread = QThread(self)
        self.discovery_worker = DiscoveryWorker(text, options, max_items=self.discovery_limit)
        self.discovery_had_error = False
        self.discovery_source_text = text
        self.discovery_fallback_to_queue = fallback_to_queue and not load_more
        self.discovery_worker.moveToThread(self.discovery_thread)
        self.discovery_thread.started.connect(self.discovery_worker.run)
        self.discovery_worker.item_found.connect(self._on_catalog_item_found)
        self.discovery_worker.failed.connect(self._on_discovery_failed)
        self.discovery_worker.all_done.connect(self._on_discovery_done)
        self.discovery_worker.all_done.connect(self.discovery_thread.quit)
        self.discovery_thread.finished.connect(self.discovery_worker.deleteLater)
        self.discovery_thread.finished.connect(self._cleanup_discovery_worker)

        self.import_button.setEnabled(False)
        self.start_button.setEnabled(False)
        self.clear_button.setEnabled(False)
        self.load_more_button.setEnabled(False)
        self.status_label.setText("正在读取作品列表...")
        self.discovery_thread.start()

    def _load_more_discovery(self) -> None:
        self.discovery_limit += 500
        self._start_discovery(load_more=True)

    def _add_queue_row(
        self,
        url: str,
        platform: str,
        title: str,
        publish_date: str | None = None,
        creator_name: str | None = None,
    ) -> bool:
        existing = {
            self.table.item(row, COL_LINK).text()
            for row in range(self.table.rowCount())
            if self.table.item(row, COL_LINK)
        }
        if url in existing:
            return False
        row = self.table.rowCount()
        self.table.insertRow(row)
        self._set_cell(row, COL_INDEX, str(row + 1))
        self._set_select_cell(row, checked=True)
        self._set_status_cell(row, "等待")
        self._set_cell(row, COL_PLATFORM, platform)
        self._set_cell(row, COL_CREATOR, creator_name or "待解析")
        self._set_quality_cell(row, self.quality_combo.currentText())
        display_title = f"{publish_date} {title}" if publish_date else title
        self._set_cell(row, COL_TITLE, display_title)
        self._set_cell(row, COL_PROGRESS, "0%")
        self._set_cell(row, COL_SPEED, "-")
        self._set_cell(row, COL_ETA, "-")
        self._set_cell(row, COL_LINK, url)
        return True

    @Slot(object)
    def _on_catalog_item_found(self, item: CatalogItem) -> None:
        if self._add_queue_row(
            url=item.url,
            platform=item.platform,
            title=item.title,
            publish_date=item.publish_date,
            creator_name=item.creator_name,
        ):
            self._update_status()

    @Slot(str)
    def _on_discovery_failed(self, error: str) -> None:
        self.discovery_had_error = True
        QMessageBox.warning(self, "读取作品列表失败", error[:500])

    @Slot(int)
    def _on_discovery_done(self, count: int) -> None:
        self.load_more_button.setVisible(count >= self.discovery_limit)
        self.load_more_button.setEnabled(True)
        if count == 0:
            if self.discovery_fallback_to_queue and not self.discovery_had_error:
                added = self._add_urls(text=self.discovery_source_text, show_message=False)
                if added:
                    self.status_label.setText(f"未读取到作品列表，已自动加入 {added} 个链接。")
                else:
                    self.status_label.setText("没有读取到可下载作品，也没有新增链接。")
            else:
                self.status_label.setText("没有读取到可下载作品。")
        elif count >= self.discovery_limit:
            self.status_label.setText(f"已读取 {count} 个作品；可能还有更多，可以点击“加载更多视频”。")
        elif (
            count <= 30
            and "douyin.com" in self.discovery_source_text.lower()
        ):
            self.status_label.setText(f"已读取 {count} 个作品；如需更多，请先点“登录抖音”或“登录 YouTube”后重读。")
        else:
            self.status_label.setText(f"已读取 {count} 个作品，请勾选后开始下载。")

    @Slot()
    def _cleanup_discovery_worker(self) -> None:
        self.discovery_thread = None
        self.discovery_worker = None
        self.import_button.setEnabled(True)
        self.start_button.setEnabled(True)
        self.clear_button.setEnabled(True)
        self.load_more_button.setEnabled(True)

    def _set_cell(self, row: int, column: int, text: str) -> None:
        item = QTableWidgetItem(text)
        item.setFlags(item.flags() & ~Qt.ItemIsEditable)
        item.setTextAlignment(Qt.AlignCenter)
        if column in FULL_TEXT_TOOLTIP_COLUMNS:
            item.setToolTip(self._full_text_tooltip(text))
        self.table.setItem(row, column, item)

    def _full_text_tooltip(self, text: str) -> str:
        cleaned = (text or "").strip()
        if not cleaned:
            return ""
        max_line_length = 80
        return "\n".join(
            cleaned[index : index + max_line_length]
            for index in range(0, len(cleaned), max_line_length)
        )

    def _set_select_cell(self, row: int, checked: bool = True) -> None:
        item = QTableWidgetItem("")
        item.setFlags((item.flags() & ~Qt.ItemIsEditable) | Qt.ItemIsUserCheckable)
        item.setCheckState(Qt.Checked if checked else Qt.Unchecked)
        item.setTextAlignment(Qt.AlignCenter)
        self.table.setItem(row, COL_SELECT, item)

    def _status_colors(self, status: str) -> tuple[QColor, QColor]:
        if status == "完成":
            return QColor("#dcfce7"), QColor("#15803d")
        if status == "已跳过":
            return QColor("#f1f5f9"), QColor("#475569")
        if status == "下载中":
            return QColor("#dbeafe"), QColor("#1d4ed8")
        if status == "等待":
            return QColor("#fef3c7"), QColor("#b45309")
        if status == "等待联网":
            return QColor("#e0f2fe"), QColor("#0369a1")
        if status == "失败":
            return QColor("#fee2e2"), QColor("#b91c1c")
        if status == "已停止":
            return QColor("#e5e7eb"), QColor("#4b5563")
        return QColor("#f8fafc"), QColor("#334155")

    def _set_status_cell(self, row: int, status: str) -> None:
        item = QTableWidgetItem(status)
        item.setFlags(item.flags() & ~Qt.ItemIsEditable)
        item.setTextAlignment(Qt.AlignCenter)
        background, foreground = self._status_colors(status)
        item.setBackground(background)
        item.setForeground(foreground)
        font = item.font()
        font.setBold(True)
        item.setFont(font)
        self.table.setItem(row, COL_STATUS, item)

    def eventFilter(self, watched: QObject, event: QEvent) -> bool:
        if watched is self.table.viewport():
            if event.type() == QEvent.MouseButtonPress and event.button() == Qt.LeftButton:
                row = self._check_column_row_at_event(event)
                if row is not None:
                    item = self.table.item(row, COL_SELECT)
                    if item:
                        self.check_drag_active = True
                        self.check_drag_rows.clear()
                        current_state = int(getattr(item.checkState(), "value", item.checkState()))
                        self.check_drag_state = (
                            Qt.Unchecked
                            if current_state == Qt.CheckState.Checked.value
                            else Qt.Checked
                        )
                        self._apply_drag_check(row)
                        return True
            elif event.type() == QEvent.MouseMove and self.check_drag_active:
                row = self._check_column_row_at_event(event)
                if row is not None:
                    self._apply_drag_check(row)
                return True
            elif event.type() == QEvent.MouseButtonRelease and self.check_drag_active:
                self.check_drag_active = False
                self.check_drag_rows.clear()
                return True
        return super().eventFilter(watched, event)

    def _event_position(self, event: QEvent):
        if hasattr(event, "position"):
            return event.position().toPoint()
        return event.pos()

    def _check_column_row_at_event(self, event: QEvent) -> int | None:
        pos = self._event_position(event)
        row = self.table.rowAt(pos.y())
        column = self.table.columnAt(pos.x())
        if row < 0 or column != COL_SELECT:
            return None
        return row

    def _apply_drag_check(self, row: int) -> None:
        if row in self.check_drag_rows:
            return
        item = self.table.item(row, COL_SELECT)
        if not item:
            return
        item.setCheckState(self.check_drag_state)
        self.table.viewport().update(self.table.visualItemRect(item))
        self.check_drag_rows.add(row)

    def _set_quality_cell(self, row: int, quality: str) -> None:
        combo = CenterComboBox()
        combo.addItems(QUALITY_OPTIONS)
        combo.setCurrentText(quality if quality in QUALITY_OPTIONS else self.quality_combo.currentText())
        combo.setObjectName("TableCombo")
        self.table.setCellWidget(row, COL_QUALITY, combo)

    @Slot(QTableWidgetItem)
    def _on_table_item_clicked(self, item: QTableWidgetItem) -> None:
        if item.column() == COL_TITLE:
            self._copy_table_cell(item.row(), COL_TITLE, "标题")
        elif item.column() == COL_LINK:
            self._copy_table_cell(item.row(), COL_LINK, "链接")

    def _copy_row_link(self, row: int) -> None:
        self._copy_table_cell(row, COL_LINK, "链接")

    def _copy_table_cell(self, row: int, column: int, label: str) -> None:
        item = self.table.item(row, column)
        if not item:
            return
        QApplication.clipboard().setText(item.text())
        self.table.setCurrentCell(row, column)
        self._set_copy_tip(f"已复制第 {row + 1} 行{label}", active=True)
        old_background = item.background()
        item.setBackground(QColor("#dcfce7"))
        QTimer.singleShot(1800, lambda: self._set_copy_tip("", active=False))
        QTimer.singleShot(900, lambda item=item, old_background=old_background: item.setBackground(old_background))

    def _set_copy_tip(self, text: str, active: bool) -> None:
        self.copy_tip_label.setText(text)
        self.copy_tip_label.setProperty("active", "true" if active else "false")
        self.copy_tip_label.style().unpolish(self.copy_tip_label)
        self.copy_tip_label.style().polish(self.copy_tip_label)

    def _set_row_status(self, row: int, status: str) -> None:
        self._set_status_cell(row, status)

    def _build_options(self) -> DownloadOptions | None:
        output_dir = Path(self.output_edit.text()).expanduser()
        if not output_dir:
            QMessageBox.warning(self, "保存位置无效", "请先选择保存位置。")
            return None

        output_dir.mkdir(parents=True, exist_ok=True)
        return DownloadOptions(
            output_dir=output_dir,
            quality=self.quality_combo.currentText(),
            cookie_mode=AUTH_COOKIE_MODE,
            cookie_file=None,
            ffmpeg_dir=self.ffmpeg_dir,
        )

    def _selected_download_rows(self) -> list[int]:
        return [
            row
            for row in range(self.table.rowCount())
            if self._row_is_selected_for_download(row)
        ]

    def _fast_skip_existing_rows(self, rows: list[int], output_dir: Path) -> int:
        existing = self._existing_media_index(output_dir)
        if not existing:
            return 0

        skipped = 0
        for row in rows:
            if not self._row_matches_existing_file(row, existing):
                continue
            self._on_item_skipped(row, "保存目录中已存在对应文件，已跳过下载。")
            skipped += 1
        return skipped

    def _existing_media_index(self, output_dir: Path) -> set[tuple[str, str]]:
        if not output_dir.exists():
            return set()

        index: set[tuple[str, str]] = set()
        for file_path in output_dir.rglob("*"):
            if not file_path.is_file() or file_path.suffix.lower() not in MEDIA_FILE_SUFFIXES:
                continue
            try:
                if file_path.stat().st_size <= 0:
                    continue
            except OSError:
                continue

            stem_key = self._normalize_existing_lookup_text(file_path.stem)
            if not stem_key:
                continue
            creator_key = self._normalize_existing_lookup_text(file_path.parent.name)
            index.add((creator_key, stem_key))
            index.add(("", stem_key))
        return index

    def _row_matches_existing_file(self, row: int, existing: set[tuple[str, str]]) -> bool:
        title_item = self.table.item(row, COL_TITLE)
        if not title_item:
            return False
        title_key = self._normalize_existing_lookup_text(title_item.text())
        if not title_key:
            return False

        creator = self._row_creator_name(row)
        creator_key = self._normalize_existing_lookup_text(safe_path_name(creator)) if creator else ""
        if creator_key:
            return (creator_key, title_key) in existing
        return ("", title_key) in existing

    def _normalize_existing_lookup_text(self, text: str | None) -> str:
        cleaned = text or ""
        cleaned = re.sub(r"\.(mp4|webm|mkv|mov|m4a|mp3)$", "", cleaned, flags=re.IGNORECASE)
        cleaned = re.sub(r"[“”\"'‘’]", "", cleaned)
        cleaned = re.sub(r'[<>:"/\\|?*\r\n\t]+', " ", cleaned)
        cleaned = re.sub(r"\s*\[[A-Za-z0-9_-]{6,24}\]\s*$", "", cleaned)
        cleaned = re.sub(r"^(?P<date>\d{4})(?P<month>\d{2})(?P<day>\d{2})\s+", r"\g<date>-\g<month>-\g<day> ", cleaned)
        cleaned = re.sub(r"^(NA|N/A|None|null)\s+", "未知日期 ", cleaned, flags=re.IGNORECASE)
        cleaned = re.sub(r"\s+", " ", cleaned).strip(" .-_")
        return cleaned.casefold()

    def _start_downloads(self) -> None:
        if self.worker_thread or self.discovery_thread:
            return
        if self.table.rowCount() == 0:
            QMessageBox.information(self, "队列为空", "请先加入至少一个链接。")
            return

        options = self._build_options()
        if not options:
            return

        selected_rows = self._selected_download_rows()
        if not selected_rows:
            QMessageBox.information(self, "没有待下载项目", "请先勾选等待或失败的项目。")
            return

        self.active_download_rows = set(selected_rows)
        skipped = self._fast_skip_existing_rows(selected_rows, options.output_dir)
        items_rows = [
            row
            for row in selected_rows
            if self._row_is_selected_for_download(row)
        ]
        if not items_rows:
            self.status_label.setText("下载任务已结束。")
            self.main_progress.setValue(100)
            summary = self._download_summary_text()
            self.active_download_rows.clear()
            self.download_started_at = None
            self.total_eta_label.setText("总剩余：0秒")
            QMessageBox.information(self, "下载完成", summary)
            return
        if skipped:
            self.status_label.setText(f"已快速跳过 {skipped} 个已存在文件，正在下载剩余项目。")

        items = [
            QueueItem(
                url=self.table.item(row, COL_LINK).text(),
                platform=self.table.item(row, COL_PLATFORM).text(),
                creator_name=self._row_creator_name(row),
                quality=self._row_quality(row),
                row=row,
            )
            for row in items_rows
        ]
        if not items:
            QMessageBox.information(self, "没有待下载项目", "请先勾选等待或失败的项目。")
            return
        self.active_download_rows = set(selected_rows)

        self.worker_thread = QThread(self)
        self.worker = DownloadWorker(items, options)
        self.worker.moveToThread(self.worker_thread)
        self.worker_thread.started.connect(self.worker.run)
        self.worker.should_download_row.connect(self._answer_should_download_row)
        self.worker.item_started.connect(self._on_item_started)
        self.worker.item_progress.connect(self._on_item_progress)
        self.worker.item_finished.connect(self._on_item_finished)
        self.worker.item_skipped.connect(self._on_item_skipped)
        self.worker.item_failed.connect(self._on_item_failed)
        self.worker.item_stopped.connect(self._on_item_stopped)
        self.worker.all_done.connect(self._on_all_done)
        self.worker.all_done.connect(self.worker_thread.quit)
        self.worker_thread.finished.connect(self.worker.deleteLater)
        self.worker_thread.finished.connect(self._cleanup_worker)

        self.start_button.setEnabled(False)
        self.stop_button.setEnabled(True)
        self.clear_button.setEnabled(False)
        self.import_button.setEnabled(False)
        self.load_more_button.setEnabled(False)
        self.main_progress.setValue(0)
        self.download_started_at = time.monotonic()
        self.total_eta_label.setText("总剩余：--")
        self.worker_thread.start()

    def _row_is_selected_for_download(self, row: int) -> bool:
        select_item = self.table.item(row, COL_SELECT)
        status_item = self.table.item(row, COL_STATUS)
        url_item = self.table.item(row, COL_LINK)
        if not select_item or not status_item or not url_item:
            return False
        selected = int(getattr(select_item.checkState(), "value", select_item.checkState())) == Qt.CheckState.Checked.value
        return selected and status_item.text() in {"等待", "等待联网", "失败", "已停止"}

    @Slot(int)
    def _answer_should_download_row(self, row: int) -> None:
        if self.worker:
            self.worker.receive_row_check(self._row_is_selected_for_download(row))

    def _row_creator_name(self, row: int) -> str | None:
        item = self.table.item(row, COL_CREATOR)
        if not item:
            return None
        creator = item.text().strip()
        if creator in {"", "待解析", "未知作者"}:
            return None
        return creator

    def _row_quality(self, row: int) -> str:
        widget = self.table.cellWidget(row, COL_QUALITY)
        if isinstance(widget, QComboBox):
            return widget.currentText()
        item = self.table.item(row, COL_QUALITY)
        if item and item.text().strip():
            return item.text().strip()
        return self.quality_combo.currentText()

    def _stop_downloads(self) -> None:
        if self.worker:
            self.worker.cancel()
            self.stop_button.setEnabled(False)
            self.status_label.setText("正在停止当前下载，请稍等几秒。")

    def _clear_queue(self) -> None:
        self.table.setRowCount(0)
        self.main_progress.setValue(0)
        self.download_started_at = None
        self.waiting_for_network = False
        self.network_check_timer.stop()
        self.total_eta_label.setText("总剩余：--")
        self.load_more_button.setVisible(False)
        self._update_status()

    def _select_all_rows(self) -> None:
        for row in range(self.table.rowCount()):
            status_item = self.table.item(row, COL_STATUS)
            select_item = self.table.item(row, COL_SELECT)
            if status_item and select_item and status_item.text() in {"等待", "等待联网", "失败", "已停止"}:
                select_item.setCheckState(Qt.Checked)

    def _invert_rows(self) -> None:
        for row in range(self.table.rowCount()):
            status_item = self.table.item(row, COL_STATUS)
            select_item = self.table.item(row, COL_SELECT)
            if status_item and select_item and status_item.text() in {"等待", "等待联网", "失败", "已停止"}:
                select_item.setCheckState(Qt.Unchecked if select_item.checkState() == Qt.Checked else Qt.Checked)

    @Slot(int)
    def _on_item_started(self, row: int) -> None:
        self._set_row_status(row, "下载中")
        self._set_cell(row, COL_PROGRESS, "0%")

    @Slot(int, dict)
    def _on_item_progress(self, row: int, info: dict[str, Any]) -> None:
        status = info.get("status")
        if info.get("filename"):
            self._set_cell(row, COL_TITLE, Path(str(info["filename"])).name)

        if status == "downloading":
            total = info.get("total_bytes") or info.get("total_bytes_estimate") or 0
            downloaded = info.get("downloaded_bytes") or 0
            percent = int(downloaded * 100 / total) if total else 0
            self._set_cell(row, COL_PROGRESS, f"{percent}%")
            self._set_cell(row, COL_SPEED, info.get("_speed_str", "-").strip())
            self._set_cell(row, COL_ETA, info.get("_eta_str", "-").strip())
        elif status == "finished":
            self._set_cell(row, COL_PROGRESS, "处理中")
            self._set_cell(row, COL_SPEED, "-")
            self._set_cell(row, COL_ETA, "-")

        self._update_main_progress()

    @Slot(int, str)
    def _on_item_finished(self, row: int, file_name: str) -> None:
        self._set_row_status(row, "完成")
        self._set_cell(row, COL_TITLE, file_name)
        self._set_cell(row, COL_PROGRESS, "100%")
        self._update_main_progress()

    @Slot(int, str)
    def _on_item_skipped(self, row: int, message: str) -> None:
        self._set_row_status(row, "已跳过")
        self._set_cell(row, COL_TITLE, message)
        self._set_cell(row, COL_PROGRESS, "100%")
        self._set_cell(row, COL_SPEED, "-")
        self._set_cell(row, COL_ETA, "-")
        self._update_main_progress()

    @Slot(int, str)
    def _on_item_failed(self, row: int, error: str) -> None:
        if is_network_error_text(error):
            self._set_row_status(row, "等待联网")
            self._set_cell(row, COL_TITLE, "网络中断，恢复联网后自动重试")
            self._set_cell(row, COL_SPEED, "-")
            self._set_cell(row, COL_ETA, "-")
            select_item = self.table.item(row, COL_SELECT)
            if select_item:
                select_item.setCheckState(Qt.Checked)
            self.waiting_for_network = True
            if self.worker:
                self.worker.cancel()
            self._start_network_wait()
            self._update_main_progress()
            return
        self._set_row_status(row, "失败")
        self._set_cell(row, COL_TITLE, error[:180])
        self._update_main_progress()

    @Slot(int)
    def _on_item_stopped(self, row: int) -> None:
        self._set_row_status(row, "已停止")
        self._set_cell(row, COL_PROGRESS, self.table.item(row, COL_PROGRESS).text() if self.table.item(row, COL_PROGRESS) else "0%")
        self._set_cell(row, COL_SPEED, "-")
        self._set_cell(row, COL_ETA, "-")
        self._update_main_progress()

    @Slot()
    def _on_all_done(self) -> None:
        if self.waiting_for_network:
            self.status_label.setText("网络中断，已暂停；恢复联网后会自动继续下载。")
            return
        self.status_label.setText("下载任务已结束。")
        summary = self._download_summary_text()
        self.active_download_rows.clear()
        self.download_started_at = None
        self.total_eta_label.setText("总剩余：0秒")
        QMessageBox.information(self, "下载完成", summary)

    @Slot()
    def _cleanup_worker(self) -> None:
        self.worker_thread = None
        self.worker = None
        self.start_button.setEnabled(True)
        self.stop_button.setEnabled(False)
        self.clear_button.setEnabled(True)
        self.import_button.setEnabled(True)
        self.load_more_button.setEnabled(True)
        if self.waiting_for_network:
            self.status_label.setText("网络中断，已暂停；正在等待恢复联网。")
        else:
            self._update_status()

    def _update_main_progress(self) -> None:
        rows = sorted(self.active_download_rows) if self.active_download_rows else list(range(self.table.rowCount()))
        total = len(rows)
        if total == 0:
            self.main_progress.setValue(0)
            self.total_eta_label.setText("总剩余：--")
            return
        progress_units = 0.0
        for row in rows:
            status_item = self.table.item(row, COL_STATUS)
            status = status_item.text() if status_item else ""
            if status in {"完成", "已跳过", "失败", "已停止"}:
                progress_units += 1.0
            elif status == "下载中":
                progress_units += self._row_progress_ratio(row)
        percent = int(progress_units * 100 / total)
        self.main_progress.setValue(max(0, min(100, percent)))
        self._update_total_eta(progress_units / total)

    def _download_summary_text(self) -> str:
        rows = sorted(self.active_download_rows) if self.active_download_rows else list(range(self.table.rowCount()))
        counts = {"完成": 0, "已跳过": 0, "失败": 0, "已停止": 0}
        for row in rows:
            status_item = self.table.item(row, COL_STATUS)
            if not status_item:
                continue
            status = status_item.text()
            if status in counts:
                counts[status] += 1
        total = sum(counts.values())
        return (
            f"本次任务已结束。\n\n"
            f"总计：{total} 个\n"
            f"完成：{counts['完成']} 个\n"
            f"跳过：{counts['已跳过']} 个\n"
            f"失败：{counts['失败']} 个\n"
            f"停止：{counts['已停止']} 个"
        )

    def _row_progress_ratio(self, row: int) -> float:
        item = self.table.item(row, COL_PROGRESS)
        if not item:
            return 0.0
        text = item.text().strip().rstrip("%")
        try:
            return max(0.0, min(1.0, float(text) / 100))
        except ValueError:
            return 0.0

    def _update_total_eta(self, progress_ratio: float) -> None:
        if not self.download_started_at or progress_ratio <= 0:
            self.total_eta_label.setText("总剩余：--")
            return
        if progress_ratio >= 1:
            self.total_eta_label.setText("总剩余：0秒")
            return
        elapsed = max(time.monotonic() - self.download_started_at, 0.1)
        remaining = elapsed * (1 - progress_ratio) / progress_ratio
        self.total_eta_label.setText(f"总剩余：{self._format_total_eta(remaining)}")

    def _format_total_eta(self, seconds: float) -> str:
        seconds_int = max(0, int(seconds))
        hours, remainder = divmod(seconds_int, 3600)
        minutes, sec = divmod(remainder, 60)
        if hours:
            return f"{hours}时{minutes:02d}分"
        if minutes:
            return f"{minutes}分{sec:02d}秒"
        return f"{sec}秒"

    def _start_network_wait(self) -> None:
        if not self.network_check_timer.isActive():
            self.network_check_timer.start()
        self.status_label.setText("网络中断，已暂停；恢复联网后会自动继续下载。")
        self._start_network_check()

    def _start_network_check(self) -> None:
        if not self.waiting_for_network or self.worker_thread or self.network_check_thread:
            return
        self.network_check_thread = QThread(self)
        self.network_check_worker = NetworkCheckWorker(self._network_probe_urls())
        self.network_check_worker.moveToThread(self.network_check_thread)
        self.network_check_thread.started.connect(self.network_check_worker.run)
        self.network_check_worker.finished.connect(self._on_network_check_finished)
        self.network_check_worker.finished.connect(self.network_check_thread.quit)
        self.network_check_thread.finished.connect(self.network_check_worker.deleteLater)
        self.network_check_thread.finished.connect(self._cleanup_network_check_worker)
        self.network_check_thread.start()

    def _network_probe_urls(self) -> list[str]:
        platforms: set[str] = set()
        for row in range(self.table.rowCount()):
            status_item = self.table.item(row, COL_STATUS)
            platform_item = self.table.item(row, COL_PLATFORM)
            if status_item and status_item.text() == "等待联网" and platform_item:
                platforms.add(platform_item.text())

        urls: list[str] = []
        if "抖音" in platforms:
            urls.append("https://www.douyin.com/")
        if "YouTube" in platforms:
            urls.append("https://www.youtube.com/generate_204")
        if not urls:
            urls.append("https://www.baidu.com/")
        return urls

    @Slot(bool)
    def _on_network_check_finished(self, online: bool) -> None:
        if not self.waiting_for_network:
            return
        if not online:
            self.status_label.setText("仍未恢复联网，稍后自动重试。")
            return

        self.network_check_timer.stop()
        self.waiting_for_network = False
        self.status_label.setText("网络已恢复，正在自动继续下载。")
        for row in range(self.table.rowCount()):
            status_item = self.table.item(row, COL_STATUS)
            if status_item and status_item.text() == "等待联网":
                self._set_row_status(row, "等待")
        QTimer.singleShot(500, self._start_downloads)

    @Slot()
    def _cleanup_network_check_worker(self) -> None:
        self.network_check_thread = None
        self.network_check_worker = None


def main() -> int:
    app = QApplication(sys.argv)
    window = MainWindow()
    window.show()
    return app.exec()


if __name__ == "__main__":
    raise SystemExit(main())
