#!/usr/bin/env python3
"""Generates the import test fixtures (GPX, TCX, FIT) independently of the app's own writers.

Run: python3 tools/make_fixtures.py app/src/test/resources/fixtures
"""
import math, struct, sys, os
from datetime import datetime, timezone, timedelta

OUT = sys.argv[1] if len(sys.argv) > 1 else "."
os.makedirs(OUT, exist_ok=True)
FIT_EPOCH = 631065600

def iso(dt): return dt.strftime("%Y-%m-%dT%H:%M:%SZ")

def loop(lat0, lon0, n, radius_m, step_s, start):
    pts = []
    for i in range(n):
        a = 2 * math.pi * i / n
        dlat = radius_m * math.sin(a) / 111320.0
        dlon = radius_m * (1 - math.cos(a)) / (111320.0 * math.cos(math.radians(lat0)))
        pts.append((start + timedelta(seconds=i * step_s), lat0 + dlat, lon0 + dlon, 30 + 10 * math.sin(a * 2), 140 + int(20 * i / n)))
    return pts

# --- GPX: Strava-style run, two segments (a pause), HR in gpxtpx, type "running" -----------------
start = datetime(2026, 9, 12, 22, 30, 0, tzinfo=timezone.utc)  # 06:30 Beijing
pts = loop(31.2304, 121.4737, 120, 400, 5, start)
seg2 = [(t + timedelta(seconds=120), la, lo, e, h) for (t, la, lo, e, h) in loop(31.2304, 121.4737, 60, 400, 5, pts[-1][0])]
with open(os.path.join(OUT, "morning_run.gpx"), "w") as f:
    f.write('<?xml version="1.0" encoding="UTF-8"?>\n<gpx creator="StravaGPX" version="1.1" xmlns="http://www.topografix.com/GPX/1/1" '
            'xmlns:gpxtpx="http://www.garmin.com/xmlschemas/TrackPointExtension/v1">\n')
    f.write(f' <metadata><time>{iso(start)}</time></metadata>\n <trk>\n  <name>Morning Run &amp; coffee</name>\n  <type>running</type>\n')
    for seg in (pts, seg2):
        f.write('  <trkseg>\n')
        for (t, la, lo, e, h) in seg:
            f.write(f'   <trkpt lat="{la:.7f}" lon="{lo:.7f}"><ele>{e:.1f}</ele><time>{iso(t)}</time>'
                    f'<extensions><gpxtpx:TrackPointExtension><gpxtpx:hr>{h}</gpxtpx:hr></gpxtpx:TrackPointExtension></extensions></trkpt>\n')
        f.write('  </trkseg>\n')
    f.write(' </trk>\n</gpx>\n')

# --- TCX: Garmin-style ride with lap totals ------------------------------------------------------
rstart = datetime(2026, 9, 13, 1, 0, 0, tzinfo=timezone.utc)
ride = loop(39.9042, 116.4074, 200, 1500, 3, rstart)
with open(os.path.join(OUT, "weekend_ride.tcx"), "w") as f:
    f.write('<?xml version="1.0" encoding="UTF-8"?>\n<TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2" '
            'xmlns:ns3="http://www.garmin.com/xmlschemas/ActivityExtension/v2">\n <Activities>\n  <Activity Sport="Biking">\n')
    f.write(f'   <Id>{iso(rstart)}</Id>\n   <Lap StartTime="{iso(rstart)}">\n    <TotalTimeSeconds>597</TotalTimeSeconds>\n'
            '    <DistanceMeters>9420.5</DistanceMeters>\n    <Calories>310</Calories>\n    <Track>\n')
    for (t, la, lo, e, h) in ride:
        f.write(f'     <Trackpoint><Time>{iso(t)}</Time><Position><LatitudeDegrees>{la:.7f}</LatitudeDegrees><LongitudeDegrees>{lo:.7f}</LongitudeDegrees></Position>'
                f'<AltitudeMeters>{e:.1f}</AltitudeMeters><HeartRateBpm><Value>{h}</Value></HeartRateBpm></Trackpoint>\n')
    f.write('    </Track>\n   </Lap>\n  </Activity>\n </Activities>\n</TrainingCenterDatabase>\n')

# --- FIT: hike with a compressed-timestamp record, a big-endian definition and a developer field --
def crc16(data, crc=0):
    tbl = [0x0000, 0xCC01, 0xD801, 0x1400, 0xF001, 0x3C00, 0x2800, 0xE401, 0xA001, 0x6C00, 0x7800, 0xB401, 0x5000, 0x9C01, 0x8801, 0x4400]
    for b in data:
        tmp = tbl[crc & 0xF]; crc = (crc >> 4) & 0x0FFF; crc = crc ^ tmp ^ tbl[b & 0xF]
        tmp = tbl[crc & 0xF]; crc = (crc >> 4) & 0x0FFF; crc = crc ^ tmp ^ tbl[(b >> 4) & 0xF]
    return crc

def semi(d): return int(round(d * (2 ** 31) / 180.0))
hstart = datetime(2026, 9, 14, 0, 0, 0, tzinfo=timezone.utc)
hike = loop(30.2741, 120.1551, 90, 600, 10, hstart)
ts0 = int(hstart.timestamp()) - FIT_EPOCH
body = bytearray()
# file_id (local 0, little endian)
body += bytes([0x40, 0, 0]) + struct.pack('<HB', 0, 3) + bytes([0, 1, 0, 1, 2, 0x84, 4, 4, 0x86])
body += bytes([0x00]) + struct.pack('<BHI', 4, 1, ts0)
# developer_data_id (207) + field_description (206) for one uint16 developer field, local 3
body += bytes([0x43, 0, 0]) + struct.pack('<HB', 207, 1) + bytes([3, 1, 0x02])
body += bytes([0x03, 0])
body += bytes([0x43, 0, 0]) + struct.pack('<HB', 206, 4) + bytes([0, 1, 0x02, 1, 1, 0x02, 2, 1, 0x02, 3, 8, 0x07])
body += bytes([0x03, 0, 0, 0x84]) + b'cadence\x00'
# record definition (local 1, big endian, with one 2-byte developer field)
body += bytes([0x61, 0, 1]) + struct.pack('>HB', 20, 5) + bytes([253, 4, 0x86, 0, 4, 0x85, 1, 4, 0x85, 2, 2, 0x84, 3, 1, 0x02])
body += bytes([1, 0, 2, 0])  # 1 dev field: num 0, size 2, dev index 0
for i, (t, la, lo, e, h) in enumerate(hike):
    ts = int(t.timestamp()) - FIT_EPOCH
    alt = int(round((e + 500) * 5))
    if i == 5:
        # compressed timestamp header for local 1: bit7 | local<<5 | (ts & 0x1F)
        body += bytes([0x80 | (1 << 5) | (ts & 0x1F)])
        body += struct.pack('>IiiHB', 0xFFFFFFFF, semi(la), semi(lo), alt, h) + b'\x00\x00'
        continue
    body += bytes([0x01]) + struct.pack('>IiiHB', ts, semi(la), semi(lo), alt, h) + b'\x00\x07'
# session (local 2, little endian): timestamp, start_time, total_timer_time, total_distance, sport, sub_sport, total_calories
end_ts = int(hike[-1][0].timestamp()) - FIT_EPOCH
body += bytes([0x42, 0, 0]) + struct.pack('<HB', 18, 7) + bytes([253, 4, 0x86, 2, 4, 0x86, 8, 4, 0x86, 9, 4, 0x86, 5, 1, 0, 6, 1, 0, 11, 2, 0x84])
body += bytes([0x02]) + struct.pack('<IIIIBBH', end_ts, ts0, 890 * 1000, 377000, 17, 0, 255)
hdr = bytearray(struct.pack('<BBHI4s', 14, 0x20, 2132, len(body), b'.FIT'))
hdr += struct.pack('<H', crc16(hdr))
data = bytes(hdr) + bytes(body)
data += struct.pack('<H', crc16(data))
open(os.path.join(OUT, "west_lake_hike.fit"), "wb").write(data)
print("fixtures written to", OUT)
