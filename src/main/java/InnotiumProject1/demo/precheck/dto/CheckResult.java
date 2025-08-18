package InnotiumProject1.demo.precheck.dto;


//record : 불변 데이터 홀더 -> 자동으로 getter, equals, tostring 생성
public record CheckResult(String checkFactorName, //점검 항목 이름(포트, dns, ping
                          boolean ok, // 성공.실패
                          String message, //부가 설명
                          Long ms) { //소요시간
}
