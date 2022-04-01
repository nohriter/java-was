package webserver;

import db.DataBase;
import db.SessionDataBase;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.HttpRequestUtils;
import util.IOUtils;

public class RequestHandler extends Thread {

    private static final Logger log = LoggerFactory.getLogger(RequestHandler.class);

    private Socket connection;

    public RequestHandler(Socket connectionSocket) {
        this.connection = connectionSocket;
    }

    public void run() {
        log.debug("New Client Connect! Connected IP : {}, Port : {}", connection.getInetAddress(),
            connection.getPort());

        try (InputStream in = connection.getInputStream(); OutputStream out = connection.getOutputStream()) {
            // TODO 사용자 요청에 대한 처리는 이 곳에 구현하면 된다.
            BufferedReader br = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8));

            HttpRequest httpRequest = new HttpRequest(br);
            HttpResponse httpResponse = new HttpResponse(out);

            if (httpRequest.getPath().equals("/user/create")) {
                User user = new User(
                    httpRequest.getParameter("userId"),
                    httpRequest.getParameter("password"),
                    httpRequest.getParameter("name"),
                    httpRequest.getParameter("email")
                );

                try {
                    DataBase.addUser(user);
                    httpResponse.response302Header("/index.html");
                } catch (IllegalArgumentException e) {
                    log.debug("exception: {}", e.getMessage());
                    httpResponse.response302Header("/user/form.html");
                }
                return;
            }

            if (httpRequest.getPath().equals("/user/login")) {
                User user = DataBase.findUserById(httpRequest.getParameter("userId"));
                if (user == null) {
                    httpResponse.response302Header("/user/login_failed.html");
                    return;
                }
                if (!user.getPassword().equals(httpRequest.getParameter("password"))) {
                    httpResponse.response302Header("/user/login_failed.html");
                    return;
                }
                String sessionId = UUID.randomUUID().toString();
                log.debug("return cookie: {}", sessionId);
                SessionDataBase.save(sessionId, user.getUserId());
                httpResponse.response302WithCookieHeader("/index.html", sessionId);
                return;
            }

            if (httpRequest.getPath().equals("/user/logout")) {
                Map<String, String> cookies = HttpRequestUtils.parseCookies(
                    httpRequest.getHeader("Cookie"));
                String sessionId = cookies.get("sessionId");
                log.debug("sessionId = {}", sessionId);
                if (sessionId == null) {
                    httpResponse.response302Header("/index.html");
                    return;
                }
                httpResponse.response302WithExpiredCookieHeader("/index.html", sessionId);
                SessionDataBase.remove(sessionId);
                return;
            }

            if (httpRequest.getPath().equals("/user/list")) {
                Map<String, String> cookies = HttpRequestUtils.parseCookies(
                    httpRequest.getHeader("Cookie"));
                String sessionId = cookies.get("sessionId");
                log.debug("sessionId = {}", sessionId);
                if (sessionId == null) {
                    httpResponse.response302Header("/index.html");
                    return;
                }

                //동적으로 HTML생성
                List<User> users = DataBase.findAll();

                byte[] listHtmlByte = createListHtmlByte(users);
                httpResponse.writeBody(listHtmlByte);
                httpResponse.response200Header();
                httpResponse.responseBody();
                return;
            }

            byte[] body = IOUtils.readRequestResource(httpRequest.getPath());
            httpResponse.writeBody(body);
            httpResponse.response200Header();
            httpResponse.responseBody();

        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private byte[] createListHtmlByte(List<User> users) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\r\n");
        sb.append("<html lang=\"en\">\n");
        sb.append("<head>\n");
        sb.append("  <meta charset=\"UTF-8\">\n");
        sb.append("  <title>Title</title>\n");
        sb.append("</head>\n");
        sb.append("<body>\n");
        sb.append("<table>\n");
        sb.append("  <thead>\n");
        sb.append("  <tr>\n");
        sb.append("    <th>#</th>\n");
        sb.append("    <th>사용자 아이디</th>\n");
        sb.append("    <th>이름</th>\n");
        sb.append("    <th>이메일</th>\n");
        sb.append("    <th>사용자 아이디</th>\n");
        sb.append("    <th></th>\n");
        sb.append("  </tr>\n");
        sb.append("  </thead>\n");
        sb.append("  <tbody>\n");
        for (int i = 0; i < users.size(); i++) {
            sb.append("  <tr>\n");
            sb.append("    <th scope=\"row\">" + (i + 1) + "</th>\n");
            sb.append("    <td>" + users.get(i).getUserId() + "</td>\n");
            sb.append("    <td>" + users.get(i).getName() + "</td>\n");
            sb.append("    <td>" + users.get(i).getEmail() + "</td>\n");
            sb.append("    <td><a href=\"#\" role=\"button\">수정</a></td>\n");
            sb.append("  </tr>\n");
        }
        sb.append("</tbody>\n");
        sb.append("</table>\n");
        sb.append("</body>\n");
        sb.append("</html>\n");
        final byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        return bytes;
    }

}
