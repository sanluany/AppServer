package com.app;


import org.mindrot.jbcrypt.BCrypt;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.time.LocalDateTime;
import java.lang.String;
import java.time.format.DateTimeFormatter;
import java.util.UUID;


public class AppServer {
    private static int port;
    private static String ip;
    private static DataBase dataBase;
    private static boolean listening = true;
    private static final int MAX_CLIENTS = 10;
    private static final AppServerThread[] APP_SERVER_THREADS = new AppServerThread[MAX_CLIENTS];
    private static ServerSocket serverSocket;
    private static Socket clientSocket;
    private static String APPID;
    private static String configValues[] = new String[7];

    private static class AppServerThread extends Thread {
        private Socket socket = null;
        private URL url;
        private UUID uuid;
        private String username = "Anonymous";

        private enum Privilege {GUEST, USER, ADMIN}

        private Privilege privilege;


        private AppServerThread(UUID uuid, Socket socket) {
            super();
            this.uuid = uuid;
            this.socket = socket;
            privilege = Privilege.GUEST;
        }

        String getPrivilegeValue() {
            if (privilege.equals(Privilege.ADMIN))
                return "ADMIN";
            else if (privilege.equals(Privilege.USER))
                return "USER";
            else
                return "GUEST";
        }

        boolean userIsLoggedIn(String username) {
            username = username.toLowerCase();
            for (int i = 0; i < APP_SERVER_THREADS.length; i++)
                if (APP_SERVER_THREADS[i] != null && (APP_SERVER_THREADS[i].username.equals(username)))
                    return true;
            return false;
        }

        String processData(String[] request) {
            int state = Integer.parseInt(request[0]);
            String answer = null;
            switch (state) {
                case 10: { // Registration
                    if (privilege.equals(Privilege.GUEST)) {
                        answer = dataBase.selectRecordFromDB(1, request);
                        if ((answer).equals("80")) { // if username is free
                            dataBase.addRecordToDB(2, request); // add new user to db;
                            answer = "80-Username " + request[1] + " has been successfully registered!";
                        } else {
                            return answer;
                        }
                    } else {
                        answer = "88-You are already registered!";
                    }
                    break;
                }
                case 11: { //Login
                    if ((privilege.equals(Privilege.GUEST)) & (!userIsLoggedIn(request[1]))) {
                        String[] respond = (dataBase.selectRecordFromDB(2, request)).split("-");
                        if (respond[0].equals("80")) {
                            answer = "82-Welcome " + request[1] + "!";
                            System.out.println("[INFO] User "+request[1]+" has just logged in");
                            if (respond[1].equals("ADMIN"))
                                privilege = Privilege.ADMIN;
                            else if (respond[1].equals("USER"))
                                privilege = Privilege.USER;
                            username = request[1];
                        } else {
                            answer = respond[0] + "-" + respond[1];
                        }
                    } else {
                        answer = "88-You are already logged in";
                    }
                    break;
                }
                case 21: {
                    if ((privilege.equals(Privilege.USER)) || (privilege.equals(Privilege.ADMIN))) {
                        String country = request[1].toLowerCase();
                        country = country.replaceAll("&", "and");
                        country = country.replaceAll("[^A-Za-z]", " ");
                        country = country.replaceAll("\\s+", "_");
                        if (country.equals("Switzerland"))
                            return "80-None";
                        else {
                            System.out.println("[INFO] Searching for information about " + country);
                            try {
                                url = new URL("https://en.wikipedia.org/w/api.php?action=parse&format=json&page=" + country);
                            } catch (MalformedURLException e) {
                                e.printStackTrace();
                                System.out.println("[ERROR] Search for " + country + " failed");
                            }
                            String response;
                            try {
                                response = makeRequest(url, 1);// 1 - Means that request associates with capital search;
                            } catch (IOException e) {
                                return "88-ERROR!Wrong Country: " + e.getMessage();
                                // e.printStackTrace();
                            }
                            int i = response.indexOf("Capital");
                            if (i < 0) { //if no Capital word was found
                                return "Sorry, Could not find information about " + country;
                                //go out from method
                            }
                            int j = response.indexOf("</a>", i);
                            String capital;
                            try {
                                capital = response.substring(i, j);
                            } catch (StringIndexOutOfBoundsException e) {
                                e.printStackTrace();
                                return "88-Sorry, Could not find information about " + country;
                            }
                            i = capital.lastIndexOf(">");
                            capital = capital.substring(i + 1);
                            //searching weather
                            try {
                                url = new URL("http://api.openweathermap.org/data/2.5/forecast?q=" + capital + "&units=metric&APPID=" + APPID);
                                response = makeRequest(url, 1);// 2 - Means that request associates with weather search;
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            //Getting temperature
                            i = response.indexOf("\"temp\":");
                            j = response.indexOf(",", i);
                            String temperature = response.substring(i, j);
                            i = temperature.indexOf(":");
                            j = temperature.lastIndexOf(".");
                            try {
                                temperature = temperature.substring(i + 1, j);
                            } catch (StringIndexOutOfBoundsException e) {
                                //The temperature is already integer number, continue;
                            }
                            //Getting weather
                            i = response.indexOf("description");
                            j = response.indexOf(",", i);
                            String weather = response.substring(i, j - 1);
                            i = weather.lastIndexOf("\"");
                            weather = weather.substring(i + 1);
                            answer = "Information about " + country + "-The capital of " + country + " is " + capital + ".-" + "Local weather: " + weather + ", " + temperature + Character.toString((char) 176) + " C";
                            String[] data = new String[4];
                            data[0] = username;
                            data[1] = uuid.toString();
                            data[2] = "Information about " + country;
                            data[3] = answer;
                            dataBase.addRecordToDB(1, data);
                            answer = "80-" + answer;
                        }
                    } else {
                        answer = "88-You are not allowed to perform such action";
                    }
                    break;
                }
                case 31: {
                    answer = "80-";
                    if (privilege.equals(Privilege.ADMIN)) {
                        int j = 0;// need to count active sessions
                        for (int i = 0; i < MAX_CLIENTS; i++) {
                            if (APP_SERVER_THREADS[i] != null) {
                                j++;
                                answer += j + ")UUID:" + APP_SERVER_THREADS[i].uuid.toString().replaceAll("-", "_") + "-" +
                                        "Username:" + APP_SERVER_THREADS[i].username + " [" + APP_SERVER_THREADS[i].getPrivilegeValue() + "]" + "-";
                            }
                        }
                    }
                    break;
                }
                case 32: {
                    if (privilege.equals(Privilege.ADMIN)) {
                        for (int i = 0; i < APP_SERVER_THREADS.length; i++)
                            try {
                                if (APP_SERVER_THREADS[i] != null)
                                    APP_SERVER_THREADS[i].socket.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        AppServer.listening = false;
                        try {
                            serverSocket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    break;
                }
            }
            return answer;
        }

        @Override
        public void run() {
            try (PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));) {
                String inputLine;
                System.out.println("[INFO] Connection established with " + uuid);
                out.println(getPrivilegeValue() + "-" + "80" + "-" + "Connection established!");
                while ((inputLine = in.readLine()) != null) {
                    String[] userData = inputLine.split("-");
                    if (userData[0].equals("90"))
                        break;
                    String answer = processData(userData);
                    if (answer != null) {
                        System.out.println("[INFO] Answer to " + username + ": " + answer);
                        out.println(getPrivilegeValue() + "-" + answer);
                    }
                }
            } catch (IOException e) {
                System.out.println("[WARNING] Connection has been reset with " + uuid);

            } finally {
                try {
                    socket.close();
                } catch (IOException e) {

                }
            }
            System.out.println("[INFO] Terminated connection with " + uuid);
        }
    }

    static void loadConfig() {
        int i = 0;
        try {
            //for (String value : Files.readAllLines(Paths.get("src/main/resources/config.cfg"))) {   // run from IDE
                for (String value : Files.readAllLines(Paths.get("config.cfg"))) {  // run from build jar
                    configValues[i] = value.substring(value.indexOf("=") + 1);
                    if (i == 0)
                        AppServer.port = Integer.parseInt(configValues[i]);
                    if (i == 1)
                        AppServer.ip = configValues[i];
                    if (i == 2)
                        AppServer.APPID = configValues[i];
                    i++;
                }
                System.out.println("[INFO] Config is loaded");
            } catch(IOException e){
                e.printStackTrace();
                System.out.println("[ERROR] Unable to load a config");
            }

        }

    public static void main(String[] args) {
        System.out.println("\n[INFO] Starting server..");
        loadConfig();
        dataBase = new DataBase(configValues);
        dataBase.connectToDB();
        try {
            serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress("Home-pc", port));
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("[INFO] Server ready");
        while (listening) {
            try {
                clientSocket = serverSocket.accept();
            } catch (IOException e) {
                if (listening)
                    System.out.println("[ERROR] Server socket has been closed");
                else
                    System.out.println("[INFO] Server socket has been closed");
            }
            for (int i = 0; i < MAX_CLIENTS; i++) {
                if (APP_SERVER_THREADS[i] == null) {
                    UUID uuid = UUID.randomUUID();
                    (APP_SERVER_THREADS[i] = new AppServerThread(uuid, clientSocket)).start();
                    System.out.println("[INFO] New client registered " + "UUID=" + uuid.toString());
                    break;
                } else {
                    if (!APP_SERVER_THREADS[i].isAlive()) {
                        System.out.println("[INFO] User "+APP_SERVER_THREADS[i].username+" has logged out");
                        APP_SERVER_THREADS[i] = null;
                    }
                }
            }
        }
        System.out.println("Shutting down..");
        for (int i = 0; i < MAX_CLIENTS; i++)
            if (APP_SERVER_THREADS[i] != null) {
                try {
                    APP_SERVER_THREADS[i].socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
    }


    private static String makeRequest(URL url, int category) throws IOException { //category = search for: 1 - capital, 2 -weather
        HttpURLConnection connection = null;
        String status = null;
        try {
            connection = (HttpURLConnection) url.openConnection();
        } catch (ClassCastException e) {
            e.printStackTrace();
        }
        // connection.setInstanceFollowRedirects(true);
        connection.setRequestMethod("GET");
        switch (category) {
            case 1: {
                status = connection.getHeaderField("MediaWiki-API-Error");
                break;
            }
            case 2: {
                // status = connection.getHeaderField("MediaWiki-API-Error");
                break;
            }
        }
        if (status != null)
            throw new IOException(status);
        BufferedReader br = null;
        if (connection != null) {
            try {
                br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            } catch (IOException | NullPointerException e) {
                //   e.printStackTrace();
            }
        }
        String response;
        StringBuilder content = new StringBuilder();
        if (br != null) {
            try {
                while ((response = br.readLine()) != null) {
                    content.append(response);
                }
                br.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return content.toString();

    }

    static class DataBase {
        private String hostName;
        private String dbName;
        private String user;
        private String password;
        private String url;
        private Connection connection = null;

        DataBase(String[] values) {
            hostName = values[3];
            dbName = values[4];
            user = values[5];
            password = values[6];

        }

        void connectToDB() {
            System.out.println("[INFO] Connecting to database...");
            url = String.format("jdbc:sqlserver://%s:1433;database=%s;user=%s;password=%s;encrypt=true;hostNameInCertificate=*.database.windows.net;loginTimeout=30;", hostName, dbName, user, password);
            try {
                Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
                connection = DriverManager.getConnection(url);
                System.out.println("[INFO] Database is connected");
            } catch (Exception e) {
                System.out.println("{ERROR} Unable to connect to database");
                System.out.println(e.getMessage());
                e.printStackTrace();
            }
        }

        static String hashPassword(String data) {
            String salt = BCrypt.gensalt(12);
            return BCrypt.hashpw(data, salt);
        }

        void addRecordToDB(int type, String[] data) {
            String query = "";
            //Get current date time
            LocalDateTime now = LocalDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String formatDateTime = now.format(formatter);
            if (type == 1) {
                data[0] = data[0].toLowerCase();
                query = "INSERT INTO History (Username, UUID, Date, Request,Response) VALUES ('" + data[0] + "', '" + data[1] + "', '" + formatDateTime + "', '" + data[2] + "', '" + data[3] + "');";
            }
            if (type == 2) {
                String pass = hashPassword(data[2]);
                data[1] = data[1].toLowerCase();
                query = "INSERT INTO AUTH(Username, Password, Privilege, CreationDate) VALUES ('" + data[1] + "','" + pass + "','" + "USER" + "','" + formatDateTime + "');";
            }
            try (Statement statement = connection.createStatement();
            ) {
                statement.executeUpdate(query);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        String selectRecordFromDB(int type, String[] request) {
            String result = null;
            if (type == 1) { //Check if username is already exists
                String query = "SELECT Username FROM Auth WHERE Username='" + request[1] + "';";
                try (Statement statement = connection.createStatement()) {
                    ResultSet resultSet = statement.executeQuery(query);
                    if (resultSet.next()) {
                        result = "88-Username " + request[1] + " is already exists";
                    } else {
                        // result = 80 + "Username " + request + " is free";
                        result = "80";
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
            if (type == 2) { //Logging in
                String query = "SELECT Username, Password, Privilege FROM Auth WHERE Username='" + request[1] + "';";
                try (Statement statement = connection.createStatement()) {
                    ResultSet resultSet = statement.executeQuery(query);
                    if (resultSet.next()) {
                        password = resultSet.getString("Password");
                        if (BCrypt.checkpw(request[2], password)) { // If passwords matches
                            result = "80-" + resultSet.getString("Privilege");//OK
                        } else {
                            result = "88-Incorrect username or password";//ERROR
                        }
                    } else {
                        // result = 80 + "Username " + request + " is free";
                        result = "88-Username " + request[1] + " doesn't exist";
                    }
                } catch (SQLException | NullPointerException e) {
                    e.printStackTrace();
                }
            }
            return result;
        }
    }

}

