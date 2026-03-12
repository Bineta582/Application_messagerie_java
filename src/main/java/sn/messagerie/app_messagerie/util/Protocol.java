package sn.messagerie.app_messagerie.util;

public final class Protocol {

    private static final String SEP = "|";
    private static final String SEP_REGEX = "\\|";

    private Protocol() {}

    public static final String REGISTER = "REGISTER";
    public static final String LOGIN = "LOGIN";
    public static final String LOGOUT = "LOGOUT";
    public static final String SEND_MSG = "SEND_MSG";
    public static final String GET_HISTORY = "GET_HISTORY";
    public static final String GET_CONVERSATIONS = "GET_CONVERSATIONS";
    public static final String GET_ONLINE_USERS = "GET_ONLINE_USERS";
    public static final String GET_ALL_USERS = "GET_ALL_USERS";

    public static final String REGISTER_OK = "REGISTER_OK";
    public static final String REGISTER_ERROR = "REGISTER_ERROR";
    public static final String LOGIN_OK = "LOGIN_OK";
    public static final String LOGIN_ERROR = "LOGIN_ERROR";
    public static final String LOGOUT_OK = "LOGOUT_OK";
    public static final String MSG = "MSG";
    public static final String MSG_OK = "MSG_OK";
    public static final String MSG_ERROR = "MSG_ERROR";
    public static final String USERS_LIST = "USERS_LIST";
    public static final String CONVERSATIONS_LIST = "CONVERSATIONS_LIST";
    public static final String HISTORY_START = "HISTORY_START";
    public static final String HISTORY_END = "HISTORY_END";
    public static final String USER_ONLINE = "USER_ONLINE";
    public static final String USER_OFFLINE = "USER_OFFLINE";
    public static final String ERROR = "ERROR";

    public static String build(String command, String... args) {
        StringBuilder sb = new StringBuilder(command);
        if (args != null) {
            for (String arg : args) {
                sb.append(SEP).append(arg == null ? "" : arg.replace("\n", " ").replace("\r", " "));
            }
        }
        return sb.toString();
    }

    public static String[] split(String message) {
        if (message == null || message.isEmpty()) {
            return new String[]{""};
        }
        return message.split(SEP_REGEX, -1);
    }
}
