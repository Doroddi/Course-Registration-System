package com.doroddi.courseregistration.health;

import org.springframework.stereotype.Component;

@Component
public class InitialDataReadiness {
    // 초기화 스레드의 완료 상태를 HTTP 요청 스레드에 공개한다.
    private volatile boolean ready;

    public boolean isReady() {
        return ready;
    }

    public void markReady() {
        ready = true;
    }
}
