package top.iceclean.chatspace.realtime.server;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author : Ice'Clean
 * @date : 2023-01-21
 */
@Component
@ConfigurationProperties(prefix = "chatspace.websocket")
public class ServerProperties {
    public Timeout timeout = new Timeout();
    public Register register = new Register();
    public Ssl ssl = new Ssl();

    public String getServerId() {
        return register.getIp() + ":" + register.getPort();
    }

    /** 超时控制 */
    public static class Timeout {
        /** 读超时，3 min */
        private int read = 180;
        /** 写超时，无 */
        private int write = 0;
        /** 所有超时，无 */
        private int idle = 0;

        public int getRead() {
            return read;
        }

        public void setRead(int read) {
            this.read = read;
        }

        public int getWrite() {
            return write;
        }

        public void setWrite(int write) {
            this.write = write;
        }

        public int getIdle() {
            return idle;
        }

        public void setIdle(int idle) {
            this.idle = idle;
        }
    }

    /** 服务注册控制 */
    public static class Register {
        /** Nacos 的 IP 地址 */
        private String nacosIp;
        /** 服务注册的 IP 地址 */
        private String ip;
        /** 服务注册的端口号 */
        private int port;
        /** 最大负载用户 */
        private int maxUserNum;
        /** 受理的空间 */
        private List<Integer> accessSpace;

        public String getNacosIp() {
            return nacosIp;
        }

        public void setNacosIp(String nacosIp) {
            this.nacosIp = nacosIp;
        }

        public String getIp() {
            return ip;
        }

        public void setIp(String ip) {
            this.ip = ip;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public List<Integer> getAccessSpace() {
            return accessSpace;
        }

        public void setAccessSpace(List<Integer> accessSpace) {
            this.accessSpace = accessSpace;
        }

        public int getMaxUserNum() {
            return maxUserNum;
        }

        public void setMaxUserNum(int maxUserNum) {
            this.maxUserNum = maxUserNum;
        }
    }

    /** SSL 认证 */
    public static class Ssl {
        /** SSL 的证书位置和密码 */
        private String file;
        private String password;

        public String getFile() {
            return file;
        }

        public void setFile(String file) {
            this.file = file;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public Timeout getTimeout() {
        return timeout;
    }

    public void setTimeout(Timeout timeout) {
        this.timeout = timeout;
    }

    public Register getRegister() {
        return register;
    }

    public void setRegister(Register register) {
        this.register = register;
    }

    public Ssl getSsl() {
        return ssl;
    }

    public void setSsl(Ssl ssl) {
        this.ssl = ssl;
    }
}
