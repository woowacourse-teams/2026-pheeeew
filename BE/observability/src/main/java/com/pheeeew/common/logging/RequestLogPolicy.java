package com.pheeeew.common.logging;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

public interface RequestLogPolicy {

    Map<String, String> requestValues(HttpServletRequest request);

    List<RequestTimingStage> timingStages();
}
