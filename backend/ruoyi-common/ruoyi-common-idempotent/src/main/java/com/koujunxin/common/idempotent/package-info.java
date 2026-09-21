/**
 * 可复用幂等组件。与业务写入同一 JDBC 事务；失败回滚后不得留下成功占位。
 */
package com.koujunxin.common.idempotent;
