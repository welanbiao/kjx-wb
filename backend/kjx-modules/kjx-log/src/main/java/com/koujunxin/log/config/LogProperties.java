package com.koujunxin.log.config;

/**
 * kjx.log.* 配置。ClickHouse 默认关闭，避免 M1 默认 compose 引入分析栈。
 */
public class LogProperties {

    private boolean redactEnabled = true;
    private final ClickHouse clickhouse = new ClickHouse();

    public boolean isRedactEnabled() {
        return redactEnabled;
    }

    public void setRedactEnabled(boolean redactEnabled) {
        this.redactEnabled = redactEnabled;
    }

    public ClickHouse getClickhouse() {
        return clickhouse;
    }

    public static LogProperties defaults() {
        return new LogProperties();
    }

    public static class ClickHouse {
        /**
         * 关闭时不创建 ClickHouse 客户端与缺省 OutboxPublisher。
         */
        private boolean enabled = false;
        private String url = "";
        private String username = "default";
        private String password = "";
        private int timeoutMs = 2000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public int getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
        }

        public boolean ready() {
            return enabled && url != null && !url.isBlank();
        }
    }
}
