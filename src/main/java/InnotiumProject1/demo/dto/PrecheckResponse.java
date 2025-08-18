package InnotiumProject1.demo.dto;

import java.util.List;

public record PrecheckResponse(String ip, String user, List<CheckResult> checks) {
}
