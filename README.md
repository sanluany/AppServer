
# AppServer
The application allows user to search a small piece of information about interested country. The information is getting by using
[MediaWiki API](https://www.mediawiki.org/wiki/API:Main_page) and [OpenWeatherMap API](https://openweathermap.org/api)

This project consists of server and client. The server is responsible for:
* User registration
* User authentication
* Searching for information
* Providing some service options for administrator

The client sends request to the server and wait for an answer.

MS SQL Server is used for manage a database, where user's and service information is stored. There are 2 tables:  
> Auth table is used for registration and authentication;  
> ```SQL
> CREATE TABLE Auth (   
> ID int IDENTITY(1,1) PRIMARY KEY,  
> Username VARCHAR(20) NOT NULL UNIQUE,  
> Password VARCHAR(255) NOT NULL,
> Privilege VARCHAR(5) NOT NULL,
> CreationDate DATETIME,
> LastVisitTime DATETIME,
> );
> ```  
  
> History table is used for storing a history of requests and responses
> ```SQL
> CREATE TABLE History (
> ID int IDENTITY(1,1) PRIMARY KEY,
> Username VARCHAR(20),
> UUID CHAR(36),
> Date DATETIME,
> Request TEXT,
> Response TEXT
> );
> ```  
#  Demonstration of work  

> Server:
>
> ![server](https://user-images.githubusercontent.com/31934687/30433356-f130e5ea-996c-11e7-8c24-354266fa3bc4.gif)
>
> Client:
>
> ![client](https://user-images.githubusercontent.com/31934687/30433357-f14e3db6-996c-11e7-914e-fa29617cfbb3.gif)
>


The action that user is allowed to perform depends on level of privilege. There are 3 levels:
- GUEST - Only allowed to register and log in
- USER - Allowed to search information
- ADMIN - Allowed to see list of active sessions and shut down the server.

> Demonstration of work under admin privileges
>
>![admin](https://user-images.githubusercontent.com/31934687/30434136-1dd6da1c-996f-11e7-935f-9b82f8d45a4e.gif)

# Configuration of the server

In order to start the server, a **config.cfg** file must be filled with important data:
1. **port** - The port that will be used by server (e.g. 4455)
2. **hostname** - Hostname of the computer
3. **APPID** - An unique key given by [OpenWeatherMap API](https://openweathermap.org/api)
4. **dataBaseHostName** - Hostname of the SQL Server
5. **dataBaseName** - Name of database
6. **username** - Username that is used to access the database
7. **password** - Password that is used to access the database  
  
The **config.cfg** gile must be placed in the same folder with the **AppServer.jar**  

# Download
You can download both server and client [here](https://github.com/sanluany/AppServer/releases)  
<br>
This project is written for education purposes. 
