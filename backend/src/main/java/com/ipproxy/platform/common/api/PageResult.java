package com.ipproxy.platform.common.api;
import java.util.List;
public record PageResult<T>(int page, int size, long total, List<T> items) {}
