import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;

/*
 * Jeœli co najmniej jeden z graczy zrezygnuje z wczytania gry, to wtedy zostanie rozpoczêta nowa gra
 * 
 */


public class Server {
	private final int port = 6666;
	private ServerSocket serverSocket;		

	public GameState gameState;
	private volatile boolean isGameRunning = false;
	public volatile int numOfClients=0;
	
	public Connection connection = null;
	public Statement statement = null;
	public final String databaseName = "tictactoe.db";
	public final String tableName = "GameTable".toUpperCase();

	private class Pair<L,R> {
	    private L l;
	    private R r;
	    public Pair(L l, R r){
	        this.l = l;
	        this.r = r;
	    }
	    public L getL(){ return l; }
	    public R getR(){ return r; }
	    public void setL(L l){ this.l = l; }
	    public void setR(R r){ this.r = r; }
	}
	
	private ArrayList<Pair<Character,Integer>> movementList = null; //required for loading gamestate
	
	
	
	
	/*
	 * 
	 * MAIN
	 * 
	 */
	
	public static void main(String [] args) {
		new Server();
	}
	
	/*
	 * 
	 * CONSTRUCTOR
	 * 
	 */
	public Server() {
		try {
			serverSocket = new ServerSocket(port);
			ServerThread[] player = new ServerThread[2];
			
			//Establishing connection with database
			if(!connectWithDb()) {
				System.out.println("Unnable to connect with SQL server! Shutting down!");
				System.exit(-1);
			}
			
			//Regardless of whether the game gonna be loaded or not, we have to init gameState
			gameState = new GameState();
			
			if(!checkIfTableExists(tableName)) {
				System.out.println("New game will start!");
				createTable(tableName);
				ResultSet rs = queryDb("SELECT * FROM "+tableName+";");
				
				while(rs.next()) {
					System.out.println("Player char: "+(rs.getString("playerChar")).charAt(0));
					System.out.println("FieldNum: "+rs.getInt("fieldNum")+"\n");
				}
				
			} else {
				ResultSet rs = queryDb("SELECT * FROM "+tableName+";");
				//If we stop the program, the table in database will stay, but it may be empty or partially filled
				if(rs.next()) {
					System.out.println("Game will be loaded!");
					loadMovements();
					loadMovementsToGameState();
				}else {
					//Table in database exists, but it's empty
					System.out.println("Table was not created, because previous exists, but it's empty!");
				}
			}

			
			//Listening on socket till the players will be connected with server
			while(numOfClients < 2) {
				player[numOfClients] = new ServerThread(serverSocket.accept(), numOfClients);
				System.out.println("Z serwerem polaczyl sie klient!");
				numOfClients++;
			}
			
			player[0].setOpponent(player[1]);
			player[1].setOpponent(player[0]);
			
			
			//Starting threads which service the customers
			for(int i=0;i<2;i++)
				player[i].start(); //in these functions are included loading functions
			
			
			System.out.println("Z serwerem polaczyly sie 2 osoby. Mozna rozpoczac gre!");
			isGameRunning=true;
				
		}catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	
	/*
 	* 
 	* Thread that services client
 	* 
 	*/
	
	class ServerThread extends Thread{
		
		private ServerThread opponentThread;
		private char playerChar;
		private Socket socket;
		
		BufferedReader in;
		PrintWriter out;
		
		private int getPlayerId() {
			if(playerChar == 'x')
				return 0;
			else return 1;
		}
		
		
		ServerThread(Socket socket, int clientNum){
			this.socket = socket;
			
			if(clientNum == 0)
				playerChar = 'x';
			else
				playerChar = 'o';
		}
		
		public void setOpponent(ServerThread opponent) {
			this.opponentThread = opponent;
		}
		public void run() {
			try {
				in = new BufferedReader(
	                    new InputStreamReader(socket.getInputStream()));
	            out = new PrintWriter(socket.getOutputStream(), true);
	            
	            //3x out.printl
	            out.println(playerChar);
	            out.flush();
	            out.println("Witaj kliencie "+playerChar);    
	            out.flush();
	            
	            if(movementList != null) {
	            	String numOfMovements = Integer.toString(movementList.size());
	            	out.println(numOfMovements);
	            	
	            	for(int i=0;i<movementList.size();i++) {
	            		int mIndex = movementList.get(i).getR();
	            		char mChar = movementList.get(i).getL();
	            		out.println(mChar+String.valueOf(mIndex));
	            	}
	            }else 
	            	out.println("0");
	            
	           
	            if(playerChar == gameState.getCurrentPlayer())
	            	out.println("Game started! Your turn!");	 
	            else
	            	out.println("Game started! Wait for your opponent!");
	            out.flush();	
	            
	            String input;
	            while (isGameRunning) {   
	            	input = in.readLine();
	           
                    String currentMoveCommand = gameState.getCurrentPlayer()+"m"; 
                    if(isGameRunning && input.startsWith(currentMoveCommand)) {	
                    	int index = Integer.parseInt(String.valueOf(input.substring(2)));
                    	
                    	System.out.println("Index = "+index);
                    	if(isMovementValid(index)) {
                    		gameState.setValue(index,getPlayerId());
                    		updateDb("INSERT INTO "+tableName+" VALUES (NULL,'"+playerChar+"',"+index+");");
                    		
                    		
                        	out.println("MOVED"+index);
                        	out.flush();
                        	opponentThread.out.println("OPPONENT_MOVED"+index);
                        	opponentThread.out.flush();

                        	if(checkIfSbWon(index)) {
                        		out.println("WIN");
                        		out.flush();
                        		opponentThread.out.println("DEFEAT");
                        		opponentThread.out.flush();
                        		isGameRunning =false;
                        		                        		
                        		out.println("EXIT");
                        		out.flush();
                        		opponentThread.out.println("EXIT");
                        		opponentThread.out.flush();
                        		
                        		deleteTable(tableName);
                        	}else if(checkIfTie()) {
                        		out.println("TIE");
                        		out.flush();
                        		opponentThread.out.println("TIE");
                        		opponentThread.out.flush();
                        		isGameRunning =false;
                        		
                        		out.println("EXIT");
                        		out.flush();
                        		opponentThread.out.println("EXIT");
                        		opponentThread.out.flush();
                        		
                        		deleteTable(tableName);	
                        	}
                         	gameState.changeCurrentPlayer();	
                    	}else {
                    		System.out.println("Illegal movement detected!");
                    		out.println("MOVEMENT_ILLEGAL");
                    		out.flush();
                    	}
                    }else if(isGameRunning){
                    	out.println("OPPONENT_MOVEMENT");
                    	out.flush();
                    }
                }
	            
	            
			}catch(Exception e) {
				System.out.println("Player disconnected the server!");
				
				
				if(isGameRunning) {
					isGameRunning = false;
					System.out.println("Disconnected from db!");
					disconnectFromDb();
					
				}	
			}
		}
		
	}


	/*
	 * 
	 * Database managment functions
	 * 
	 */
	
	public boolean connectWithDb() {
		try {
			Class.forName("org.hsqldb.jdbcDriver");
			connection = DriverManager.getConnection("jdbc:hsqldb:file:db/"+databaseName);
			System.out.println("Connection with database established!");
			return true;
		}catch(SQLException e) {
			e.printStackTrace();
			return false;
		}catch(ClassNotFoundException e) {
			e.printStackTrace();
			return false;
		}
	}
	public boolean disconnectFromDb() {
		try {
			connection.close();
			return true;
		}catch(SQLException e) {
			e.printStackTrace();
			return false;
		}
	}
	//jesli tabela bedzie istniala, to gracze zostana zapytani czy chca kontynuowac gre
	public boolean checkIfTableExists(String tabName){
		try{
			DatabaseMetaData dbm = connection.getMetaData();
			ResultSet tables = dbm.getTables(null, null, tabName, null);
			if (tables.next()) 
				return true;	
			else 
				return false;
		}catch(SQLException e){
			return false;
		}
	}
	public void createTable(String tableName) {
		String sqlCommand = "CREATE TABLE IF NOT EXISTS "+tableName + " ("
				+ "id INTEGER GENERATED BY DEFAULT AS IDENTITY(START WITH 1, INCREMENT BY 1) NOT NULL PRIMARY KEY, "
				+ "playerChar varchar(1) NOT NULL,"
				+ "fieldNum INTEGER NOT NULL);";
		if(!updateDb(sqlCommand))
			System.out.println("Table was not created!");
		
	}
	public void deleteTable(String tableName) {
		String sqlCommand = "DROP TABLE "+tableName+";";
		updateDb(sqlCommand);
	}
	public boolean updateDb(String sqlCommand) {
		try {
			statement = connection.createStatement();
			statement.executeUpdate(sqlCommand);
			statement.close();
			return true;
		}catch(SQLException e) {
			e.printStackTrace();
			return false;
		}
	}
	public ResultSet queryDb(String sqlCommand) {
		try {
			statement = connection.createStatement();
			ResultSet rs = statement.executeQuery(sqlCommand);
			statement.close();
			return rs;
		}catch(SQLException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	//// END OF DB FUNCTIONS ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	
	//Different methods		
	boolean checkIfTie() {
		for(int i=0;i<GameState.boardSize*GameState.boardSize;i++)
			if(gameState.getValue(i)==-1)
				return false;
		return true;
	}
	
	//we want to check only vicinity of newly addded point not the entire board
	boolean checkIfSbWon(int index) {
		//auxiliary class
		class Comp{
			public int computeX(int index) {
				return index%GameState.boardSize;
			}
			public int computeY(int index) {
				return index/GameState.boardSize;
			}
			public int computeIndex(int x, int y) {
				return y * GameState.boardSize + x;
			}
		}
		
		Comp c = new Comp();
		//compute cooridinates of newly added point
		int posX = c.computeX(index);
		int posY = c.computeY(index);
		
		//System.out.println("posX = "+posX+"\tposY = "+posY);	
		
		/////////////////////////////////////////////////
		//Checking in vertical axis		
		for(int i=0;i<5;i++) {
			
			boolean toContinue = false;

			for(int j=4;j>=0;j--) {
				//check if index is not out of range
				if(posY-j+i < 0 || posY-j+i >= GameState.boardSize) {
					toContinue = true;
					break;
				}				
				int otherPointIndex = c.computeIndex(posX, posY-j+i);
				
				if(gameState.getValue(otherPointIndex) != gameState.getValue(index)) {
					toContinue = true;
					break;
				}
			}			
			if(toContinue)
				continue;
			else {
				//If we were able to get out of the loop, it means that 4 other points have the same index as our point
				return true;
			}			
		}
		
		//////////////////////////////////////////////
		//Checking in horizontal axis
		for(int i=0;i<5;i++) {
			
			boolean toContinue = false;

			for(int j=4;j>=0;j--) {
				//check if index is not out of range
				if(posX-j+i < 0 || posX-j+i >= GameState.boardSize) {
					toContinue = true;
					break;
				}				
				int otherPointIndex = c.computeIndex(posX-j+i, posY);
				
				if(gameState.getValue(otherPointIndex) != gameState.getValue(index)) {
					toContinue = true;
					break;
				}
			}			
			if(toContinue)
				continue;
			else {
				//If we were able to get out of the loop, it means that 4 other points have the same index as our point
				return true;
			}			
		}
		
		//////////////////////////////////////////////
		//Checking diagonally part1
		for(int i=0;i<5;i++) {
			
			boolean toContinue = false;

			for(int j=4;j>=0;j--) {
				//check if index is not out of range
				if(posX-j+i < 0 || posY-j+i < 0 || posX-j+i >= GameState.boardSize || posY-j+i >= GameState.boardSize) {
					toContinue = true;
					break;
				}				
				int otherPointIndex = c.computeIndex(posX-j+i, posY-j+i);
				if(gameState.getValue(otherPointIndex) != gameState.getValue(index)) {
					toContinue = true;
					break;
				}
			}			
			if(toContinue)
				continue;
			else {
				//If we were able to get out of the loop, it means that 4 other points have the same index as our point
				return true;
			}			
		}
		
		//////////////////////////////////////////////
		//Checking diagonally part2
		for(int i=0;i<5;i++) {
		
			boolean toContinue = false;
		
			for(int j=4;j>=0;j--) {
				//check if index is not out of range
				if(posX-j+i < 0 || posY+j-i < 0 || posX-j+i >= GameState.boardSize || posY+j-i >= GameState.boardSize) {
						toContinue = true;
						break;
				}				
				int otherPointIndex = c.computeIndex(posX-j+i, posY+j-i);
				if(gameState.getValue(otherPointIndex) != gameState.getValue(index)) {
					toContinue = true;
					break;
				}
			}			
			if(toContinue)
				continue;
			else {
				//If we were able to get out of the loop, it means that 4 other points have the same index as our point
				return true;
			}			
		}
		
		return false;
	}
	private boolean isMovementValid(int index) {
		
		
		if(gameState.getValue(index) == -1)
			return true;
		else 
			return false;
	}

	//Loading of movements
	public void loadMovements() {
		ResultSet rs = queryDb("SELECT * FROM "+tableName+";");
		movementList = new ArrayList<Pair<Character,Integer>>();
		
		try {
			while(rs.next()) {
				//id, playerChar, fieldNum - col names
				Pair<Character,Integer> pair = new Pair<Character,Integer>((rs.getString("playerChar")).charAt(0), rs.getInt("fieldNum"));
				movementList.add(pair);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	public void loadMovementsToGameState() {
		int playerId, fieldNum;
		for(int i=0;i<movementList.size();i++) {
			playerId = ((movementList.get(i)).getL() == 'x')? 0 : 1;
			fieldNum = (movementList.get(i)).getR();
			
			gameState.setValue(fieldNum, playerId);
			
		}
	}
}
