"""
Spike 부하 패턴 (LoadTestShape).

사용:
    locust -f load-test/shape_spike.py -f load-test/locustfile.py \
        --host=http://localhost:8000 --headless \
        WriterJourney ReviewerJourney BrowsingLoad

부하 단계 (총 ~7분):
    0~120s    100u baseline   (warm-up)
    120~150s  100→800u ramp   (spike)
    150~330s  800u sustained  (peak)
    330~390s  800→100u drain  (cool-down)
    390s 이후 종료

환경변수로 조정 가능:
    SPIKE_BASE_USERS    baseline 사용자 수 (기본 100)
    SPIKE_PEAK_USERS    peak 사용자 수    (기본 800)
    SPIKE_BASE_SEC      baseline 지속 시간 (기본 120)
    SPIKE_RAMP_SEC      ramp 시간         (기본 30)
    SPIKE_PEAK_SEC      peak 지속 시간    (기본 180)
    SPIKE_DRAIN_SEC     drain 시간        (기본 60)
"""
import os
from locust import LoadTestShape


class SpikeShape(LoadTestShape):
    BASE = int(os.environ.get("SPIKE_BASE_USERS", "100"))
    PEAK = int(os.environ.get("SPIKE_PEAK_USERS", "800"))
    BASE_SEC = int(os.environ.get("SPIKE_BASE_SEC", "120"))
    RAMP_SEC = int(os.environ.get("SPIKE_RAMP_SEC", "30"))
    PEAK_SEC = int(os.environ.get("SPIKE_PEAK_SEC", "180"))
    DRAIN_SEC = int(os.environ.get("SPIKE_DRAIN_SEC", "60"))

    @property
    def _stages(self):
        # (end_time, users, spawn_rate)
        t1 = self.BASE_SEC
        t2 = t1 + self.RAMP_SEC
        t3 = t2 + self.PEAK_SEC
        t4 = t3 + self.DRAIN_SEC
        ramp_rate = max(1, (self.PEAK - self.BASE) // max(1, self.RAMP_SEC))
        drain_rate = max(1, (self.PEAK - self.BASE) // max(1, self.DRAIN_SEC))
        return [
            (t1, self.BASE, max(1, self.BASE // 10)),
            (t2, self.PEAK, ramp_rate),
            (t3, self.PEAK, ramp_rate),
            (t4, self.BASE, drain_rate),
        ]

    def tick(self):
        run_time = self.get_run_time()
        for end_time, users, rate in self._stages:
            if run_time < end_time:
                return (users, rate)
        return None
