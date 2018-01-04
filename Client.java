import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Toolkit;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.border.Border;

public class Client extends JFrame{
	private static final long serialVersionUID = 1L;

	private Socket socket;
	
	boolean isGameRunning = false;
	
	
	private final int port = 6666;
	private final String hostname = "localhost";
	
	private BufferedReader input;
	private PrintWriter output;

	private final int FIELDSIZE=40;
	
	private BufferedImage graySquare;
	private BufferedImage redcirc;
	private BufferedImage greenx;
	private char playerChar;
		
	private Painter painter;
	private JLabel infoPanel;
	

	//Used only for drawing, currPlayer variables doesnt matter
	private GameState gameState = new GameState();

	public static void main(String[] args) {
		new Client();
	}

	/****************
	 * 
	 * 	CONSTRUCTOR
	 * 
	 ****************/
	public Client() {
		if(!loadImages())
			System.out.println("Error while loading images!");
		initWindow();
		
		try {
			socket = new Socket(hostname,port);
			input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			output = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
			
			//3x input.readline
			playerChar = input.readLine().charAt(0);
			setTitle(getTitle()+"      Your char is "+playerChar);
			System.out.println("S:"+input.readLine());
			System.out.println("Player char = "+playerChar);
			
			//Check if it's required to load map
			int movementNum = Integer.parseInt(input.readLine());
			if(movementNum != 0) {
				for(int i=0;i<movementNum;i++) {
					String receivedMovement = input.readLine();
					char pChar = receivedMovement.charAt(0);
					int field = Integer.parseInt(receivedMovement.substring(1));
					
					System.out.println("Otrzymano: pChar="+pChar+"\tfield="+field);
					
					gameState.setValue(field, GameState.getPlayerId(pChar));
					repaint();
					revalidate();
					try {
						Thread.sleep(500);
					}catch(InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		
			infoPanel.setText(input.readLine());
			isGameRunning = true;
			
			String response;	
			while(true) {
				response = input.readLine();
				if(response.startsWith("MOVED")) {
					infoPanel.setText("You moved! Wait for your turn!");
					gameState.setValue(Integer.parseInt(String.valueOf(response.substring(5))),getPlayerId());
				}			
				else if(response.startsWith("OPPONENT_MOVED")) {
					infoPanel.setText("Opponent moved! Your turn!");
					gameState.setValue(Integer.parseInt(String.valueOf(response.substring(14))),getOpponentId());
				}
				else if(response.equals("OPPONENT_MOVEMENT"))
					infoPanel.setText("It's not your turn!");
				else if(response.equals("MOVEMENT_ILLEGAL"))
					infoPanel.setText("Illegal movement! Check other field!");
				else if(response.equals("TIE")) {
					infoPanel.setText("You drew with the opponent!");
				}
				else if(response.equals("WIN")) {
					infoPanel.setText("You won! :)");
				}
				else if(response.equals("DEFEAT")) {
					infoPanel.setText("You lost!");
				}else if(response.equals("EXIT")) {
					Thread.sleep(2000);
					this.dispose();
					System.exit(0);
				}
				repaint();
				revalidate();
			}
		}catch(Exception e) {	
			System.out.println("Unable to connect with server!");
			System.out.println("Window will close in 3 seconds!");
			try {
				Thread.sleep(3000);
			}catch(InterruptedException e1) {
				e1.printStackTrace();
			}
			System.exit(-1);
		}
	}
	
	/************************
	 * 
	 * INIT FUNCTIONS
	 * 
	 ***********************/
	private boolean loadImages() {
		try {
			graySquare = ImageIO.read(new File("graysquare.png"));
			redcirc = ImageIO.read(new File("redcirc.png"));
			greenx = ImageIO.read(new File("greenx.png"));
		}catch(Exception e) {
			return false;
		}
		return true;
	}
	
	private void initWindow() {
		setTitle("TicTacToe Game");
		setLocationRelativeTo(null);
		
		setVisible(true);
		setLayout(new BorderLayout());
		setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		addWindowListener(new java.awt.event.WindowAdapter() {
			public void windowClosing(java.awt.event.WindowEvent windowEvent) {
		        if(!isGameRunning)
		        	System.exit(0); 
				if (JOptionPane.showConfirmDialog(null, 
		            "Are you sure to close this window?", "Really Closing?", 
		            JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION){
		            System.exit(0);
		        }
		    }        
		});
		
		setResizable(false);
		requestFocus();
			
		painter = new Painter();
		add(painter,BorderLayout.CENTER);
		
		Border blackLine = BorderFactory.createMatteBorder(3, 0, 0, 0, Color.black);
		
		infoPanel = new JLabel();
		infoPanel.setVerticalTextPosition(JLabel.CENTER);
		infoPanel.setHorizontalTextPosition(JLabel.CENTER);
		infoPanel.setHorizontalAlignment(JLabel.CENTER);
		infoPanel.setPreferredSize(new Dimension(GameState.boardSize*FIELDSIZE, 50));
		infoPanel.setBorder(blackLine);
		infoPanel.setText("Waiting for opponent!");
				
		add(infoPanel, BorderLayout.PAGE_END);
		pack();
		
		Dimension dimension = Toolkit.getDefaultToolkit().getScreenSize();
	    int x = (int) ((dimension.getWidth() - getWidth()) / 2);
	    int y = (int) ((dimension.getHeight() - getHeight()) / 2);
	    setLocation(x, y);
		
		painter.repaint();
	}

	
	/**********************
	 * 
	 * 		GETTERS 
	 * 
	 **********************/
	
	private int getPlayerId() {
		if(playerChar == 'x')
			return 0;
		else return 1;
	}
	
	private char getOpponentId() {
		if(playerChar == 'x')
			return 1;
		else 
			return 0;
	}
	
	/*************************
	 * 
	 * 			MISC
	 * 
	 *************************/
	public void showBoard() {
		for(int i=0;i<GameState.boardSize * GameState.boardSize;i++) {
			System.out.print(gameState.getValue(i)+"\t");
			if(i!=0 && i%GameState.boardSize == 0)
				System.out.println("");
		}
	}
	
	
	private class Painter extends JPanel implements MouseListener{
		private static final long serialVersionUID = 1L;
		public Painter() {
			setPreferredSize(new Dimension(GameState.boardSize*FIELDSIZE,GameState.boardSize*FIELDSIZE));
			addMouseListener(this);
			setFocusable(true);
			requestFocus();
		}
		
		public void paintComponent(Graphics g) {
			super.paintComponent(g);
			Graphics2D g2 = (Graphics2D)g;
			
			for(int i=0;i<GameState.boardSize*GameState.boardSize;) {
				g2.drawImage(graySquare, (i%GameState.boardSize) * FIELDSIZE, (i/GameState.boardSize) * FIELDSIZE, FIELDSIZE, FIELDSIZE, this);
				
				if(i/GameState.boardSize != (i+2)/GameState.boardSize) {
					if((i/GameState.boardSize)%2 == 0)
						i+=3;
					else
						i+=1;
					continue;
				}
				i+=2;
			}
			
			for(int i=0;i<GameState.boardSize*GameState.boardSize;i++) {
				if(gameState.getValue(i) != -1) {					
					if(gameState.getValue(i) == 0)
						g2.drawImage(greenx, (i%GameState.boardSize) * FIELDSIZE, (i/GameState.boardSize) * FIELDSIZE, FIELDSIZE, FIELDSIZE, this);
					else if(gameState.getValue(i) == 1)
						g2.drawImage(redcirc, (i%GameState.boardSize) * FIELDSIZE, (i/GameState.boardSize) * FIELDSIZE, FIELDSIZE, FIELDSIZE, this);
				}
			}
		}
		public void mousePressed(MouseEvent e) {
			if(!isGameRunning)
				return;
			int index = e.getX()/FIELDSIZE%GameState.boardSize+e.getY()/FIELDSIZE * GameState.boardSize;
			System.out.println(index);
			output.println(playerChar+"m"+index);
			output.flush();
		}
		public void mouseClicked(MouseEvent e) {
	
		}
		public void mouseEntered(MouseEvent arg0) {

		}
		public void mouseExited(MouseEvent arg0) {

		}
		public void mouseReleased(MouseEvent arg0) {

		}
		
	}
	
	
}
