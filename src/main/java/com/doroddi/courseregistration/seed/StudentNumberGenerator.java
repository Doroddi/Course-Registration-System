package com.doroddi.courseregistration.seed;

import java.util.ArrayList;
import java.util.List;

/** 초기 데이터용 학번을 생성한다. 발급 기록 조회나 기존 학번과의 충돌 검사는 호출자가 담당한다. */
public final class StudentNumberGenerator {
    public List<Integer> generate(int year, int departmentCode, int count) {
        if (year < 2020 || year > 2026) {
            throw new IllegalArgumentException("초기 데이터의 연도는 2020~2026이어야 합니다.");
        }
        if (departmentCode < 10 || departmentCode > 99) {
            throw new IllegalArgumentException("학과 코드는 10~99이어야 합니다.");
        }
        if (count < 0 || count > 400) {
            throw new IllegalArgumentException("연도·학과별 생성 인원은 0~400이어야 합니다.");
        }
        var numbers = new ArrayList<Integer>(count);
        for (int index = 100; index < 100 + count; index++) {
            // 정해진 자릿수로 합성하므로 난수 충돌·재시도 없이 그룹 내 유일성을 보장한다.
            numbers.add(year * 100_000 + departmentCode * 1_000 + index);
        }
        return List.copyOf(numbers);
    }
}
