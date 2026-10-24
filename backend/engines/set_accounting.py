"""Descriptive set selection for volume. No physiological load is calculated."""

def is_set_effective(set_data) -> bool:
    s = set_data.model_dump() if hasattr(set_data, "model_dump") else set_data
    if s.get("type") == "warmup":
        return False
    if s.get("isFailure") or s.get("performanceMode") in {"failure", "failed"}:
        return True
    for key in ("completedRIR", "rir", "targetRIR"):
        if s.get(key) is not None:
            return s[key] <= 4
    for key in ("completedRPE", "rpe", "targetRPE"):
        if s.get(key) is not None:
            return s[key] >= 6
    # Unspecified intensity is counted as a programmed working set, never as measured effort.
    return True
