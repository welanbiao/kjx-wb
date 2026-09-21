package com.koujunxin.member.domain;

public record PageResult<T>(java.util.List<T> list, long total) {
}
