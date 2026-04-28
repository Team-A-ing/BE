package com.readb.domain.promise;

public enum PromiseStatus {
    PENDING,    // 이행 전
    DONE,       // 이행 완료
    MISSED      // 기한 초과
}
