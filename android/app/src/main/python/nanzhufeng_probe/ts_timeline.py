"""Rebase declared HLS MPEG-TS discontinuities without changing encoded samples.

One program/PCR clock is required. Rewriting PCR, PES PTS/DTS and transport
continuity counters keeps codec configuration, packet order and encoded payload intact. The original
transport stream survives until the caller has successfully muxed the result.
"""
import os

_CLOCK_WRAP = 1 << 33
_PACKET_SIZE = 188


def _pts(packet, start):
    return (((packet[start] >> 1) & 7) << 30) | (packet[start + 1] << 22) | \
        ((packet[start + 2] >> 1) << 15) | (packet[start + 3] << 7) | (packet[start + 4] >> 1)


def _write_pts(packet, start, value):
    value %= _CLOCK_WRAP
    packet[start] = (packet[start] & 0xf0) | (((value >> 30) & 7) << 1) | 1
    packet[start + 1] = (value >> 22) & 255
    packet[start + 2] = (((value >> 15) & 127) << 1) | 1
    packet[start + 3] = (value >> 7) & 255
    packet[start + 4] = ((value & 127) << 1) | 1


def _avc_program_pids(packet, payload):
    """Read a complete PMT section; never guess a codec from arbitrary payload bytes."""
    if payload >= _PACKET_SIZE:
        return set()
    start = payload + 1 + packet[payload]
    if start + 12 > _PACKET_SIZE or packet[start] != 2:
        return set()
    end = start + 3 + ((packet[start + 1] & 15) << 8 | packet[start + 2]) - 4
    if end > _PACKET_SIZE:
        return set()
    cursor = start + 12 + ((packet[start + 10] & 15) << 8 | packet[start + 11])
    pids = set()
    while cursor + 5 <= end:
        if packet[cursor] == 0x1b:
            pids.add((packet[cursor + 1] & 31) << 8 | packet[cursor + 2])
        cursor += 5 + ((packet[cursor + 3] & 15) << 8 | packet[cursor + 4])
    return pids


def _avc_drain_packet(pid, counter, timestamp):
    # A complete AUD followed by a start-code boundary releases Android's final
    # buffered AVC picture. It contains no slice/picture and is not muxed as one.
    # ESQueue::dequeueAccessUnitH264 flushes on the next complete AUD/SPS.
    pts = bytearray(timestamp)
    pts[0] = (pts[0] & 15) | 0x20
    body = b'\x80\x80\x05' + pts + b'\x00\x00\x00\x01\x09\xf0\x00\x00\x00\x01'
    pes = b'\x00\x00\x01\xe0' + len(body).to_bytes(2, 'big') + body
    adaptation = 183 - len(pes)
    return bytes([0x47, 0x40 | (pid >> 8), pid & 255, 0x30 | ((counter + 1) & 15), adaptation, 0]) + \
        b'\xff' * (adaptation - 1) + pes


def _clock_jumps(source, cancelled):
    previous = None
    elapsed = 0
    step = None
    jumps = []
    with open(source, 'rb') as stream:
        offset = 0
        while block := stream.read(_PACKET_SIZE * 512):
            if cancelled():
                raise InterruptedError('时间轴处理已暂停或取消')
            for position in range(0, len(block) - 187, _PACKET_SIZE):
                p = block[position:position + _PACKET_SIZE]
                if p[0] != 0x47:
                    raise ValueError('HLS MPEG-TS 包无效，未发布')
                if (p[3] >> 4) & 3 not in (2, 3) or p[4] < 7 or not p[5] & 16:
                    continue
                current = (p[6] << 25) | (p[7] << 17) | (p[8] << 9) | (p[9] << 1) | (p[10] >> 7)
                if previous is not None:
                    delta = (current - previous + _CLOCK_WRAP // 2) % _CLOCK_WRAP - _CLOCK_WRAP // 2
                    if abs(delta) > 90_000:
                        elapsed += step or 0
                        jumps.append((offset + position, delta, elapsed / 90_000, step or 0))
                    else:
                        elapsed += delta
                        if delta > 0:
                            step = delta
                previous = current
            offset += len(block)
    return jumps


def _identical_adjacent_blocks(stream, start, length, cancelled):
    for offset in range(0, length, 64 * 1024):
        if cancelled():
            raise InterruptedError('时间轴处理已暂停或取消')
        size = min(64 * 1024, length - offset)
        stream.seek(start - length + offset)
        previous = stream.read(size)
        stream.seek(start + offset)
        if previous != stream.read(size):
            return False
    return True


def _proven_repeated_ranges(source, expected_discontinuities, expected_duration, expected_boundaries, cancelled):
    # Legacy overlapping writers can append one complete fragment twice. Only
    # remove exact adjacent bytes when there are excess clock resets AND a
    # declared duration is available. The input itself is never changed.
    if expected_duration is None or expected_boundaries is None:
        return []
    jumps = _clock_jumps(source, cancelled)
    excess = len(jumps) - expected_discontinuities
    if excess <= 0:
        return []
    if excess > 8:
        raise ValueError('重复分片超出安全恢复范围，原缓存已保留')
    ranges = []
    removed_seconds = 0
    total_size = os.path.getsize(source)
    with open(source, 'rb') as stream:
        for offset, delta, position_seconds, step in jumps:
            if len(ranges) == excess:
                break
            if not -900_000 < delta < -90_000:
                continue
            if any(abs(position_seconds - removed_seconds - expected) <= 0.25 for expected in expected_boundaries):
                # A declared repeat belongs to the work and must survive.
                continue
            stream.seek(offset)
            key = stream.read(4 * _PACKET_SIZE)
            begin = max(0, offset - 16 * 1024 * 1024)
            stream.seek(begin)
            prior = stream.read(offset - begin).rfind(key)
            if prior < 0:
                continue
            length = offset - (begin + prior)
            if length % _PACKET_SIZE or length <= 0:
                continue
            for start in range(max(length, offset - 16 * _PACKET_SIZE), offset + 1, _PACKET_SIZE):
                if start % _PACKET_SIZE or start + length > total_size:
                    continue
                if any(start < end and start + length > old_start for old_start, end in ranges):
                    continue
                if _identical_adjacent_blocks(stream, start, length, cancelled):
                    ranges.append((start, start + length))
                    removed_seconds += (step - delta) / 90_000
                    break
    if len(ranges) != excess:
        raise ValueError('额外时间轴切换不是可证明的重复分片，原缓存已保留')
    return sorted(ranges)


def _read_transport(stream, size, skipped):
    position = stream.tell()
    for start, end in skipped:
        if start <= position < end:
            stream.seek(end)
            position = end
        if position < start:
            size = min(size, start - position)
            break
    return stream.read(size)


def normalize_ts_discontinuities(source, expected_discontinuities, cancelled, expected_duration=None, expected_boundaries=None):
    if expected_discontinuities <= 0:
        # Even a continuous TS needs an AVC delimiter to drain Android's last
        # buffered picture. fMP4 remains untouched by the TS-only pass.
        with open(source, 'rb') as incoming:
            if incoming.read(1) != b'\x47':
                return source
    target = source + '.timeline.ts'
    partial = target + '.part'
    last_pcr = None
    pcr_pid = None
    normal_step = None
    shift = 0
    boundaries = 0
    source_counters = {}
    output_counters = {}
    counter_epochs = {}
    media_pids = set()
    avc_pids = set()
    avc_timestamps = {}
    if expected_boundaries is not None and len(expected_boundaries) != expected_discontinuities:
        raise ValueError('HLS 时间轴切换位置与清单数量不一致')
    skipped = _proven_repeated_ranges(source, expected_discontinuities, expected_duration, expected_boundaries, cancelled)
    first_corrected_pcr = None
    last_corrected_pcr = None
    try:
        with open(source, 'rb') as incoming, open(partial, 'wb') as outgoing:
            while True:
                if cancelled():
                    # Caller translates cancellation into its established task state.
                    raise InterruptedError('时间轴处理已暂停或取消')
                block = _read_transport(incoming, _PACKET_SIZE * 512, skipped)
                if not block:
                    break
                if len(block) % _PACKET_SIZE:
                    raise ValueError('HLS 时间轴切换不是完整 MPEG-TS，未发布')
                block = bytearray(block)
                for start in range(0, len(block), _PACKET_SIZE):
                    packet = memoryview(block)[start:start + _PACKET_SIZE]
                    if packet[0] != 0x47 or packet[1] & 0x80:
                        raise ValueError('HLS MPEG-TS 包无效，未发布')
                    pid = ((packet[1] & 31) << 8) | packet[2]
                    adaptation = (packet[3] >> 4) & 3
                    payload = 4
                    if adaptation in (2, 3):
                        length = packet[4]
                        if length > 183:
                            raise ValueError('MPEG-TS 适配字段无效')
                        payload += 1 + length
                        if length >= 7 and packet[5] & 0x10:
                            if pcr_pid is None:
                                pcr_pid = pid
                            if pid != pcr_pid:
                                raise ValueError('多时钟 MPEG-TS 暂不能安全合并时间轴')
                            current = (packet[6] << 25) | (packet[7] << 17) | (packet[8] << 9) | (packet[9] << 1) | (packet[10] >> 7)
                            clock_jump = False
                            if last_pcr is not None:
                                delta = (current - last_pcr + _CLOCK_WRAP // 2) % _CLOCK_WRAP - _CLOCK_WRAP // 2
                                if abs(delta) > 90_000:
                                    clock_jump = True
                                    if normal_step is None:
                                        raise ValueError('缺少连续媒体时钟，无法安全校正')
                                    shift += normal_step - delta
                                    boundaries += 1
                                elif delta > 0:
                                    normal_step = delta
                            last_pcr = current
                            corrected = (current + shift) % _CLOCK_WRAP
                            if first_corrected_pcr is None:
                                first_corrected_pcr = corrected
                            last_corrected_pcr = corrected
                            if clock_jump and expected_boundaries is not None:
                                position = ((corrected - first_corrected_pcr) % _CLOCK_WRAP) / 90_000
                                if boundaries > len(expected_boundaries) or abs(position - expected_boundaries[boundaries - 1]) > 0.25:
                                    raise ValueError('HLS 媒体时间轴切换位置与清单不一致，原缓存已保留')
                            packet[6] = corrected >> 25
                            packet[7] = (corrected >> 17) & 255
                            packet[8] = (corrected >> 9) & 255
                            packet[9] = (corrected >> 1) & 255
                            packet[10] = (packet[10] & 127) | ((corrected & 1) << 7)
                    if pid != 8191:
                        counter = packet[3] & 15
                        has_payload = adaptation in (1, 3)
                        if has_payload and pid in source_counters:
                            if counter != (source_counters[pid] + 1) % 16 and pid in media_pids:
                                if counter_epochs[pid] == boundaries:
                                    raise ValueError('非声明切换处的 MPEG-TS 媒体包不连续，未发布')
                        if has_payload:
                            source_counters[pid] = counter
                            counter_epochs[pid] = boundaries
                            output_counters[pid] = (output_counters[pid] + 1) % 16 if pid in output_counters else counter
                        if pid in output_counters:
                            packet[3] = (packet[3] & 0xf0) | output_counters[pid]
                    if adaptation not in (1, 3) or not packet[1] & 0x40 or payload + 9 > _PACKET_SIZE:
                        continue
                    avc_pids.update(_avc_program_pids(packet, payload))
                    if bytes(packet[payload:payload + 3]) != b'\x00\x00\x01':
                        continue
                    stream_id = packet[payload + 3]
                    if not (0xc0 <= stream_id <= 0xef):
                        continue
                    media_pids.add(pid)
                    flags = packet[payload + 7] >> 6
                    if flags in (2, 3):
                        if payload + 14 > _PACKET_SIZE:
                            raise ValueError('跨包 PES 时间戳暂不能安全校正')
                        _write_pts(packet, payload + 9, _pts(packet, payload + 9) + shift)
                        if pid in avc_pids:
                            avc_timestamps[pid] = bytes(packet[payload + 9:payload + 14])
                    if flags == 3:
                        if payload + 19 > _PACKET_SIZE:
                            raise ValueError('跨包 PES 时间戳暂不能安全校正')
                        _write_pts(packet, payload + 14, _pts(packet, payload + 14) + shift)
                outgoing.write(block)
            if boundaries != expected_discontinuities:
                raise ValueError('HLS 声明的时间轴切换与媒体时钟不一致，未发布')
            if skipped:
                duration = ((last_corrected_pcr - first_corrected_pcr) % _CLOCK_WRAP + (normal_step or 0)) / 90_000
                if abs(duration - expected_duration) > 0.25:
                    raise ValueError('重复分片恢复后的时长与清单不一致，原缓存已保留')
            for pid, timestamp in avc_timestamps.items():
                outgoing.write(_avc_drain_packet(pid, output_counters[pid], timestamp))
        os.replace(partial, target)
        return target
    finally:
        if os.path.exists(partial):
            os.unlink(partial)
