package com.app.wiki;

import java.io.*;
import java.net.*;

public class WikiAppClient {

    private static Socket clientSocket;
    private static boolean quit = false;
    private static BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in));

    private enum Privilege {GUEST, USER, ADMIN}

    private static Privilege privilege = Privilege.GUEST; //set starting privilege to guest

    private static class UserInterface extends Thread {

        static String userData(int state) {
            if (state == 1)
                System.out.println("\nPlease, enter username and password to log in");
            if (state == 2)
                System.out.println("\nPlease, enter username and password to register new user");
            String username, password;
            System.out.println("Enter username:");
            username = readString();
            System.out.println("Enter password:");
            password = readString();
            if ((username == null) | (password == null)) {
                System.out.println("Symbol \"-\" is not allowed!");
                return null;
            }
            if (state == 1) //1 - Login request; 2 - Registration request;
                return "11-" + username + "-" + password;
            else
                return "10-" + username + "-" + password;

        }

        static String showMenu() throws IOException { // show menu to user according to the privilege
            int choice;
            switch (privilege) {
                case GUEST: {
                    System.out.println("\nWelcome to WikiApp!");
                    System.out.println("1 - Log in");
                    System.out.println("2 - Register");
                    System.out.println("3 - Quit");
                    choice = readInt();
                    if (choice == 1)
                        return userData(1);//Login request
                    if (choice == 2)
                        return userData(2);//Registration request;
                    if (choice == 3)
                        quit = true; //Perform quit;
                    break;
                }
                case USER: {
                    System.out.println("\nWhat would you like?");
                    System.out.println("1 - Get some information about country");
                    System.out.println("2 - Quit");
                    choice = readInt();
                    if (choice == 1) {
                        System.out.println("\nWhat country?");
                        return "21" + "-" + readString(); // Request information about country
                    }
                    if (choice == 2) {
                        quit = true;
                    }
                    break;
                }
                case ADMIN: {
                    System.out.println("\nWhat would you like?");
                    System.out.println("1 - Get some information about country");
                    System.out.println("2 - Enter admin menu");
                    System.out.println("3 - Quit");
                    choice = readInt();
                    if (choice == 1) {
                        System.out.println("\nWhat country?");
                        return "21" + "-" + readString();
                    }
                    if (choice == 2) {
                        System.out.println("\n1 - Show list of active sessions");
                        System.out.println("2 - Shutdown server");
                        System.out.println("3 - Quit");
                        int adminChoice = readInt();
                        if (adminChoice == 1)
                            return "31"; // Request for list of active sessions
                        if (adminChoice == 2)
                            return "32"; //Request server to shutdown
                        if (adminChoice == 3)
                            quit = true;
                        if (adminChoice == 4)
                            return "skip";
                    }
                    if (choice == 3) {
                        quit = true;
                    }
                    break;
                }
                default: {
                    System.out.println("Unknown selection! Try again");
                    break;
                }
            }
            return null;
        }

        static int readInt() { //read integer from user
            int input = -1;
            try {
                input = Integer.parseInt(bufferedReader.readLine());
            } catch (NumberFormatException | IOException e) {
                System.out.println("Wrong input!");
            }
            return input;
        }

        static String readString() { //read string from user
            String input = null;
            try {
                input = bufferedReader.readLine();
                if (input.contains("-"))
                    return null;
            } catch (NumberFormatException | IOException e) {
                e.printStackTrace();
            }
            return input;
        }
    }


    public static void main(String[] args) throws IOException {
        UserInterface userInterface = new UserInterface();
        userInterface.start();
        do {
            try {
                int port = 4455;
                clientSocket = new Socket("10.29.60.5", port);
            } catch (ConnectException e) {
                System.out.println("\nThe server does not respond. Try again?");
                System.out.println("1 - Connect");
                System.out.println("2 - Quit");
                if (!UserInterface.readString().equals("1")) {
                    System.out.println("BYE");
                    System.exit(1);
                }
            }
        } while (clientSocket == null);
        clientSocket.setReuseAddress(true);
        String fromUser = "";
        try (PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));) {
            while (!quit) {
                Thread.sleep(50);
                String fromServer;
                if (fromUser != null && (!fromUser.equals("skip")) && (((fromServer = in.readLine()) != null))) {
                    String[] response = fromServer.split("-");
                    switch (response[0]) {
                        case "ADMIN":
                            privilege = Privilege.ADMIN;
                            break;
                        case "USER":
                            privilege = Privilege.USER;
                            break;
                        default:
                            privilege = Privilege.GUEST;
                            break;
                    }
                    if (response.length > 2) {
                        System.out.println();
                        for (int i = 2; i < response.length; i++)
                            System.out.println(response[i]);
                    }
                }
                fromUser = UserInterface.showMenu();
                if ((fromUser != null) && (!fromUser.equals("skip"))) {
                    out.println(fromUser);
                }
            }
            out.println("90");
            userInterface.join();
            clientSocket.close();
        } catch (UnknownHostException e) {
            e.printStackTrace();
            System.exit(1);
        } catch (IOException e) {
            System.out.println("The server has terminated session");
            clientSocket.close();
            System.exit(1);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("BYE!");
    }
}

