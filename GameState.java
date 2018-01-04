public class GameState {
	private char currentPlayer;
	public static final int boardSize = 20;
	private int[] board = new int[boardSize*boardSize];
	

	//Constructors
	public GameState() {
		//Initialization of board
		for(int i=0;i<boardSize*boardSize;i++)
			board[i] = -1;
		this.currentPlayer = 'x';
	}
	public GameState(GameState gs) {
		this.currentPlayer = gs.currentPlayer;
		this.board = gs.board;
	}
	public GameState(char currentPlayer, int[] board) {
		super();
		
		this.currentPlayer = currentPlayer;
		this.board = board;
	}
	
	
	//Getters/setters
	public char getCurrentPlayer() {
		return currentPlayer;
	}
	public void setCurrentPlayer(char currentPlayer) {
		this.currentPlayer = currentPlayer;
	}	
	public int getValue(int index) {
		if(index < 0 || index >= boardSize*boardSize) {
			System.out.println("Out of range!");
			return -999;
		}
		return board[index];
	}
	
	public void setValue(int index, int value) {
		if(index < 0 || index >= boardSize*boardSize) {
			System.out.println("Out of range!");
			return;
		}
		if(value < -1 || value > 1) {
			System.out.println("Incorrect value!");
			return;
		}
			
		board[index] = value;
	}

	//Misc
	public synchronized void changeCurrentPlayer() {
		if(currentPlayer=='x')
			currentPlayer = 'o';
		else
			currentPlayer = 'x';
	}
	public static int getPlayerId(char playerChar) {
		if(playerChar == 'x')
			return 0;
		else if(playerChar == 'o')
			return 1;
		else 
			return -1;
	}
	public static char getPlayerChar(int playerId) {
		if(playerId == 0)
			return 'x';
		else if(playerId == 1)
			return 'o';
		else 
			return '\0';
	}
	public void show() {
		System.out.println("CurrPlayer = "+currentPlayer);
		System.out.print("Board: ");
		for(int i=0;i<boardSize*boardSize;i++) {
			System.out.print(board[i]+"\t");
		}
	}
}
