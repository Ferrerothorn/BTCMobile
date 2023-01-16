package swissTournamentRunner;

import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.*;
import java.io.BufferedReader;
import java.io.File;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;

public class Main {

	static Tournament tourney = new Tournament();

	public static void main(String[] args) {
		showCredits();
		tourney.signUpPlayers();
		tourney.allParticipantsIn = true;
		tourney.run();
	}

	private static void showCredits() {
		Utils.postString("Welcome to FFTCG BTC, the Final Fantasy TCG Swiss Tournament Bracket Organiser!");
		Utils.postString("(August 2022 Edition - Made by Steve Dolman)");
	}
}

class Tournament {

	public static ArrayList<Player> players = new ArrayList<>();
	public static ArrayList<Player> dropped = new ArrayList<>();
	public ArrayList<Battle> currentBattles = new ArrayList<>();
	public ArrayList<Battle> totallyKosherPairings = new ArrayList<>();
	public ArrayList<Battle> completedBattles = new ArrayList<>();
	TntFileManager tntFileManager = new TntFileManager(this);
	String roundString;
	public String elo = "off";
	boolean allParticipantsIn = false;
	public static int topCutThreshold = 0;
	public int numberOfRounds = 0;
	public int roundNumber = 1;
	public String activeMetadataFile = "TournamentInProgress.tnt";
	public int predictionsMade = 0;
	public int correctPredictions = 0;
	Scanner sc = new Scanner(System.in);

	public void signUpPlayers() {
		if (activeMetadataFile.equals("TournamentInProgress.tnt")) {
			Utils.print("Enter the name of this tournament.");
			activeMetadataFile = readInput();
			if (!activeMetadataFile.contains(".tnt")) {
				activeMetadataFile += ".tnt";
			}
		}
		File file = new File(activeMetadataFile);
		if (file.exists()) {
			try {
				TntFileManager.loadTournament(this, activeMetadataFile);
				refreshScreen();
			} catch (IOException e) {
				postString("Error reading supplied file.");
			}
		} else {
			PlayerCreator playerCreator = new PlayerCreator(this);
			playerCreator.capturePlayers();
		}
	}

	private void postString(String string) {
		System.out.println(string);
	}

	public void addPlayer(String p1) {
		if (!doesPlayerExist(p1)) {
			if (p1.length() > 0) {
				String temp = Utils.sanitise(p1);
				temp = Utils.sanitise(temp);
				players.add(new Player(temp));
			}
		}
		while (numberOfRounds < (logBase2(players.size()))) {
			numberOfRounds++;
		}
		if (!allParticipantsIn) {
			postListOfConfirmedSignups();
		}
	}

	public void addLatePlayer(String p1) {
		players.add(new Player(p1));
		while (numberOfRounds < (logBase2(players.size()))) {
			numberOfRounds++;
		}
		if (!allParticipantsIn) {
			postListOfConfirmedSignups();
		}
	}

	public void postListOfConfirmedSignups() {
		Collections.sort(players);
		StringBuilder post = new StringBuilder("-=-=-Registered: " + size() + " player(s) -=-=-" + "\n");
		for (int i = 1; i <= players.size(); i++) {
			post.append(i).append(") ").append(players.get(i - 1).getName()).append("\n");
		}
		Utils.print(post.toString());
	}

	public void sortRankings() {
		Collections.sort(players);
	}

	public String getCurrentBattles(ArrayList<Battle> battles, String roundString) {
		updateParticipantStats();
		int longestPlayerNameLength = 0;
		StringBuilder battlesString = new StringBuilder(roundString + "\n");

		for (Battle b : battles) {
			if (b.getP1().getName().length() > longestPlayerNameLength) {
				longestPlayerNameLength = b.getP1().getName().length();
			}
			if (b.getP2().getName().length() > longestPlayerNameLength) {
				longestPlayerNameLength = b.getP2().getName().length();
			}
		}

		if (getElo().equals("on")) {
			Collections.sort(currentBattles);
		}

		for (Battle b : battles) {
			String playerOneString = b.getP1().getName() + " (" + b.getP1().getScore() + " pts)";
			String playerTwoString = b.getP2().getName() + " (" + b.getP2().getScore() + " pts)";

			String battleString = Utils.rpad("Table " + b.getTableNumber() + ") ", 11);
			battleString += Utils.rpad(playerOneString, longestPlayerNameLength + 8) + " vs.    ";
			battleString += Utils.rpad(playerTwoString + "       ", longestPlayerNameLength + 8);
			if (getElo().equals("on")) {
				battleString += "[" + b.getElo(b.getP1()) + "% - " + b.getElo(b.getP2()) + "%]";
			}
			battlesString.append(battleString).append("\n");
		}
		return battlesString.toString();
	}

	public boolean doesPlayerExist(String string) {
		Player p = findPlayerByName(string);
		return p != null;
	}

	public void shufflePlayers() {
		Collections.shuffle(players);
	}

	public void updateParticipantStats() {
		for (Player p : players) {
			p.updateParticipantStats(completedBattles);
		}
		sortRankings();
		for (Player p : players) {
			p.updatePositionInRankings(players);
		}
	}

	public void generatePairings(int attempts) {
		if (numberOfRounds < logBase2(getLivePlayerCount())) {
			numberOfRounds++;
		}
		if (currentBattles.size() == 0 || activeGamesWereSeeded(currentBattles)) {
			if (getLivePlayerCount() % 2 == 1) {
				boolean byeExists = checkByeExists();
				boolean byeNeeded = checkByeNeeded();
				facilitateByeAddition(byeExists, byeNeeded);
			}
			while (getLivePlayerCount() > 0 && attempts <= 100) {
				Player p1 = players.remove(0);
				if (!p1.isDropped()) {
					pairThisGuyUp(p1, currentBattles, attempts);
				} else {
					players.add(p1);
				}
			}

			currentBattles.addAll(totallyKosherPairings);
			totallyKosherPairings.clear();

			if (attempts > 100) {
				abort();
				Utils.print(generateInDepthRankings(players));
			}
			for (Battle b : currentBattles) {
				players.add(b.getP1());
				players.add(b.getP2());
			}
			int index = 0;
			for (Battle b : currentBattles) {
				b.setTableNumber(index + 1);
				index++;
			}
		}
	}

	private void facilitateByeAddition(boolean byeExists, boolean byeNeeded) {
		if (byeExists) {
			Player bye = findPlayerByName("BYE");
			if (byeNeeded) {
				if (bye.isDropped()) {
					bye.setDropped(false);
					dropped.remove(bye);
				} else {
					bye.setDropped(true);
					dropped.add(bye);
				}
			} else {
				bye = findPlayerByName("BYE");
				bye.setDropped(true);
				dropped.add(bye);
			}
		} else {
			if (byeNeeded) {
				players.add(new Player("BYE"));
			}
		}
	}

	private boolean checkByeExists() {
		return findPlayerByName("BYE") != null;
	}

	private boolean checkByeNeeded() {
		return ((getLivePlayerCount() % 2) == 1);
	}

	public String generateInDepthRankings(ArrayList<Player> ps) {

		StringBuilder participantString = new StringBuilder();
		int longestPlayerNameLength = 0;

		for (Player p : ps) {
			if (p.getName().length() > longestPlayerNameLength) {
				longestPlayerNameLength = p.getName().length();
			}
		}

		ArrayList<Player> temp = new ArrayList<>(ps);

		for (Player p : dropped) {
			temp.remove(p);
		}

		if (topCutThreshold != 0) {
			participantString.append("===Rankings - Top Cut===\n");
			for (int i = 1; i <= topCutThreshold; i++) {
				if (!temp.get(i - 1).getName().equals("BYE")) {

					String pScore = Integer.toString(temp.get(i - 1).getScore());
					String pOWR = temp.get(i - 1).getOppWr() + "%";
					String aveReceived = Double.toString(temp.get(i - 1).getAverageDamageReceived());
					String aveDealt = Double.toString(temp.get(i - 1).getAverageDamageDealt());

					participantString
							.append(Utils.rpad("" + i + ") " + temp.get(i - 1).getName() + "                         ",
									longestPlayerNameLength + 7))
							.append("   ").append(Utils.rpad("Score: " + pScore + "                         ", 15))
							.append("   ").append(Utils.rpad(("Opp WR: " + pOWR + "  "), 14)).append("  ")
							.append(Utils.rpad(("Ave. Dam. Taken: " + aveReceived + "  "), 21)).append("     ")
							.append(Utils.rpad(("Ave. Dam. Dealt: " + aveDealt + "  "), 21)).append("     ")
							.append(Utils.rpad("Win Pattern: " + temp.get(i - 1).getWinPattern(), 24)).append("  ")
							.append('\n');
				}
			}
			participantString.append("==Rankings - Qualifiers==" + "\n");
		} else {
			participantString.append("===Rankings===\n");
		}

		for (int j = topCutThreshold + 1; j <= temp.size(); j++) {
			if (!temp.get(j - 1).getName().equals("BYE")) {

				String pScore = Integer.toString(temp.get(j - 1).getScore());
				String pOWR = temp.get(j - 1).getOppWr() + "%";
				String aveReceived = Double.toString(temp.get(j - 1).getAverageDamageReceived());
				String aveDealt = Double.toString(temp.get(j - 1).getAverageDamageDealt());

				participantString
						.append(Utils.rpad("" + j + ") " + temp.get(j - 1).getName() + "                         ",
								longestPlayerNameLength + 7))
						.append("   ").append(Utils.rpad("Score: " + pScore + "                         ", 15))
						.append("   ").append(Utils.rpad(("Opp WR: " + pOWR + "  "), 14)).append("  ")
						.append(Utils.rpad(("Ave. Dam. Taken: " + aveReceived + "  "), 21)).append("     ")
						.append(Utils.rpad(("Ave. Dam. Dealt: " + aveDealt + "  "), 21)).append("     ")
						.append(Utils.rpad("Win Pattern: " + temp.get(j - 1).getWinPattern(), 24)).append("  ")
						.append('\n');
			}
		}
		return participantString.toString();
	}

	private boolean activeGamesWereSeeded(ArrayList<Battle> battles) {
		for (Battle b : battles) {
			if (!b.wasSeeded) {
				return false;
			}
		}
		return true;
	}

	public ArrayList<Player> getPlayers() {
		return players;
	}

	public ArrayList<Player> getDroppedPlayers() {
		return dropped;
	}

	public void pairThisGuyUp(Player p1, ArrayList<Battle> targetBattleList, int attempts) {

		try {
			int playerIndex = 0;

			while (true) {
				Player temp = players.get(playerIndex);
				if (!p1.getOpponentsList().contains(temp) && !temp.getOpponentsList().contains(p1)
						&& !temp.isDropped()) {
					temp = players.remove(playerIndex);
					Battle b = new Battle(p1, temp);
					targetBattleList.add(b);
					break;
				}
				playerIndex++;
			}
		} catch (Exception e) {
			if (attempts >= 100) {
				players.add(p1);
				abort();
			} else {
				disseminateBattles(currentBattles);
				players.add(p1);
				sortRankings();
				players.remove(p1);
				if (p1.getPositionInRankings() > players.size() / 2) {
					Collections.reverse(players);
				}
				pairThisGuyUp(p1, totallyKosherPairings, attempts + 1);
				sortRankings();

			}
		}
	}

	void abort() {
		disseminateBattles(currentBattles);
		disseminateBattles(totallyKosherPairings);
		sortRankings();
		allParticipantsIn = false;
	}

	public void disseminateBattles(ArrayList<Battle> battles) {
		for (Battle b : battles) {
			Player p1 = b.getP1();
			Player p2 = b.getP2();
			players.add(p1);
			players.add(p2);
		}
		battles.clear();
	}

	public void pollForResults() {
		while (currentBattles.size() > 0 && allParticipantsIn) {
			updateRoundString();
			Utils.wipePane();
			Utils.print("Enter a table number to report a score for the game.");
			Utils.print("");

			try {
				Utils.print(getCurrentBattles(currentBattles, roundString));
				String input = readInput();
				if ("admintools".equals(input)) {
					Utils.print("Admin functions enabled. (drop, add, roundrobin, repair, reopen)");
					String adminCommand = readInput();
					adminTools(adminCommand);
				} else {
					Battle b = fetchBattle(input, currentBattles);

					assert b != null;
					Utils.print("And who won in " + b.getP1().getName() + " vs. " + b.getP2().getName() + "?");
					Utils.print("1) " + b.getP1().getName() + " (" + b.getElo(b.getP1()) + "% predicted win rate)");
					Utils.print("2) " + b.getP2().getName() + " (" + b.getElo(b.getP2()) + "% predicted win rate)");
					Utils.print("0) Tied.");

					if (!((b.getP1().getName().equals("BYE") || (b.getP2().getName().equals("BYE"))))) {
						
						String winner = readInput();
						int p1dd;
						int p2dd;

						if (winner.equals("2")) {
							Utils.print("How much damage did " + b.getP2().getName() + " deal?");
							p2dd = Integer.parseInt(readInput());
							Utils.print("How much damage did " + b.getP1().getName() + " deal?");
							p1dd = Integer.parseInt(readInput());
						} else {
							Utils.print("How much damage did " + b.getP1().getName() + " deal?");
							p1dd = Integer.parseInt(readInput());
							Utils.print("How much damage did " + b.getP2().getName() + " deal?");
							p2dd = Integer.parseInt(readInput());
						}

						b.setP1DamageDealt(p1dd);
						b.setP2DamageDealt(p2dd);

						if (!b.getP1().getName().equals("BYE") && !b.getP2().getName().equals("BYE")
								&& ((winner.equals("1") && b.getElo(b.getP1()) > 50)
										|| (winner.equals("2") && b.getElo(b.getP2()) > 50))) {
							correctPredictions++;
						}
						if (b.getElo(b.getP1()) != 50) {
							predictionsMade++;
						}
						switch (winner) {
						case "1":
							currentBattles.remove(b);
							if (activeMetadataFile.contains("topCut")) {
								eliminatePlayer(b.getP2());
							}
							Utils.handleBattleWinner(b, "1");
							completedBattles.add(b);
							break;
						case "2":
							currentBattles.remove(b);
							if (activeMetadataFile.contains("topCut")) {
								eliminatePlayer(b.getP1());
							}
							Utils.handleBattleWinner(b, "2");
							completedBattles.add(b);
							break;
						case "0":
							currentBattles.remove(b);
							Utils.handleBattleWinner(b, "0");
							completedBattles.add(b);
							break;
						}
					} else {
						if (b.getP1().getName().equals("BYE")) {
							b.getP2().beats(b.getP1());
							completedBattles.add(b);
							currentBattles.remove(b);
						} else if (b.getP2().getName().equals("BYE")) {
							b.getP1().beats(b.getP2());
							completedBattles.add(b);
							currentBattles.remove(b);
						}
					}
					sortRankings();
					refreshScreen();
				}
			} catch (Exception e) {
				Utils.print("Specified table number does not exist.");
				pollForResults();
			}
			save();
		}
	}

	private void updateRoundString() {
		roundString = ("-=-=-=-ROUND " + roundNumber + "/" + numberOfRounds + "-=-=-=-");
	}

	public void refreshScreen() {
		Utils.wipePane();
		updateParticipantStats();
		Collections.shuffle(players);
		sortRankings();
		printRankings(generateInDepthRankings(players));
		getCurrentBattles(currentBattles, roundString);
		System.out.println();
		System.out.println();
	}

	private void printRankings(String generateInDepthRankings) {
		Utils.print(generateInDepthRankings);
	}

	private Battle fetchBattle(String reportUpon, ArrayList<Battle> cB) {
		if (isNumeric(reportUpon)) {
			for (Battle b : cB) {
				if (b.getTableNumber() == Integer.parseInt(reportUpon)) {
					return b;
				}
			}
		} else {
			for (Battle b : cB) {
				if (b.contains(reportUpon)) {
					return b;
				}
			}
		}
		return null;
	}

	public static boolean isNumeric(String str) {
		NumberFormat formatter = NumberFormat.getInstance();
		ParsePosition pos = new ParsePosition(0);
		formatter.parse(str, pos);
		return str.length() == pos.getIndex();
	}

	public void assignTableNumbers(ArrayList<Battle> bIP) {
		int index = 1;
		for (Battle b : bIP) {
			b.setTableNumber(index);
			index++;
		}
	}

	int logBase2(int x) {
		double logBase2ofX = Math.log(x) / Math.log(2);

		int base = (int) Math.ceil(logBase2ofX);
		if (base > 0) {
			return (int) Math.ceil(logBase2ofX);
		}
		return 0;
	}

	public void adminTools(String string) {
		switch (string.toLowerCase()) {
		case "acr":
			while (currentBattles.size() > 0) {
				Battle b = currentBattles.remove(0);
				if (b.getP1().getName().equals("BYE")) {
					Utils.handleBattleWinner(b, Integer.toString(2));
				} else if (b.getP2().getName().equals("BYE")) {
					Utils.handleBattleWinner(b, Integer.toString(1));
				} else {
					Random r = new Random();
					int win = r.nextInt(2);
					win++;
					Utils.handleBattleWinner(b, Integer.toString(win));
				}
			}
			updateParticipantStats();
			sortRankings();
			Utils.print(generateInDepthRankings(players));
			break;
		case "roundrobin":
			generateRoundRobinPairings();
			break;
		case "killall -9":
			currentBattles.clear();
			players.clear();
			break;
		default:
			Utils.print("Invalid admin command. Returning to tournament...\n");
		}
	}

	public String toggle(String onOrOff) {
		if (onOrOff.equals("on")) {
			return "off";
		}
		return "on";
	}

	private void generateRoundRobinPairings() {
		currentBattles.clear();
		for (Player p : players) {
			p.getOpponentsList().clear();
			p.getListOfVictories().clear();
		}
		players.remove(findPlayerByName("BYE"));
		this.setNumberOfRounds(1);
		this.roundNumber = 1;

		int index = 1;
		for (Player p : players) {
			for (Player q : players) {
				if (p != q && !activeBattleExists(currentBattles, p, q)) {
					Battle b = new Battle(p, q);
					b.setTableNumber(index);
					index++;
					currentBattles.add(b);
				}
			}
		}
	}

	private boolean activeBattleExists(ArrayList<Battle> battles, Player p, Player q) {
		boolean exists = false;
		for (Battle b : battles) {
			if ((b.getP1().equals(p) && b.getP2().equals(q)) || (b.getP1().equals(q) && b.getP2().equals(p))) {
				exists = true;
				break;
			}
		}
		return exists;
	}

	void setTopCut(int parseInt) {
		topCutThreshold = parseInt;
	}

	public void addBatch(String playerList) {
		String[] names = playerList.split(",");
		for (String s : names) {
			addPlayer(Utils.sanitise(Utils.sanitise(s)));
			postListOfConfirmedSignups();
		}
	}

	public void renamePlayer(String playerToRename, String newName) {
		for (Player p : players) {
			if (p.getName().equals(playerToRename)) {
				p.setName(newName);
				break;
			}
		}
		for (Battle b : currentBattles) {
			if (b.getP1().getName().equals(playerToRename)) {
				b.getP1().setName(newName);
				break;
			} else if (b.getP2().getName().equals(playerToRename)) {
				b.getP2().setName(newName);
				break;
			}
		}
	}

	public void alterTopCut(String newSize) throws NumberFormatException {
		try {
			int tC = Integer.parseInt(newSize);
			if (tC < size()) {
				setTopCut(tC);
				Utils.print("Top Cut size set to " + tC + ".\n");
			} else {
				Utils.print("Invalid - suggested top cut size is too large.");
				Utils.print("Size must be a less than the number of players.");
			}
		} catch (NumberFormatException e) {
			Utils.print("Invalid input - top cut size must be a number.");
			Utils.print("Size must be a less than the number of players.");
			Utils.print("Alternatively, enter '0' to remove the Top Cut.\n");
		}
	}

	public int size() {
		return players.size();
	}

	public void setNumberOfRounds(int newNumberOfRounds) {
		numberOfRounds = newNumberOfRounds;
	}

	public int getNumberOfRounds() {
		return numberOfRounds;
	}

	public int getTopCutThreshold() {
		return topCutThreshold;
	}

	public void alterRoundNumbers(String newMax) throws NumberFormatException {
		try {
			int newNumOfRounds = Integer.parseInt(newMax);
			if (newNumOfRounds < players.size() && newNumOfRounds >= logBase2(players.size())) {
				setNumberOfRounds(newNumOfRounds);
				Utils.print("Number of rounds updated to " + getNumberOfRounds() + ".");
				updateRoundString();
			} else {
				Utils.print("Invalid number of rounds for a Swiss tournament.");
				Utils.print("We need to have less rounds than the number of players, and at least logBase2(number of players).");
			}
		} catch (NumberFormatException e) {
			Utils.print("Illegal input - try submitting a number of rounds as a number.");
		}
	}

	public void reopenBattle(Player p1, Player p2) {
		boolean reopen = false;
		for (Player p : p1.getOpponentsList()) {
			if (p.equals(p2)) {
				p1.getOpponentsList().remove(p);
				reopen = true;
				break;
			}
		}
		for (Player p : p2.getOpponentsList()) {
			if (p.equals(p1)) {
				p2.getOpponentsList().remove(p);
				reopen = true;
				break;
			}
		}
		for (Player p : p1.getListOfVictories()) {
			if (p.equals(p2)) {
				p1.getListOfVictories().remove(p);
				reopen = true;
				break;
			}
		}
		for (Player p : p2.getListOfVictories()) {
			if (p.equals(p1)) {
				p2.getListOfVictories().remove(p);
				reopen = true;
				break;
			}
		}
		if (reopen) {
			currentBattles.add(new Battle(p1, p2));
		}
		updateParticipantStats();
	}

	public String getResultsOfAllMatchesSoFar() {
		StringBuilder results = new StringBuilder();
		for (Battle b : completedBattles) {
			results.append(b.getP1().getName()).append(" ").append(b.getP1().hasBeaten(b.getP2())).append(" [")
					.append(b.getP1DamageDealt()).append(" - ").append(b.getP2DamageDealt()).append("] ")
					.append(b.getP2().hasBeaten(b.getP1())).append(" ").append(b.getP2().getName()).append("\n");
		}
		return results.toString();
	}

	public String getResultsOfAllMatchesByPlayerSoFar(Player p) {
		StringBuilder results = new StringBuilder();
		for (Battle b : completedBattles) {
			if (b.contains(p)) {
				results.append(b.getP1().getName()).append(" ").append(b.getP1().hasBeaten(b.getP2())).append(" [")
						.append(b.getP1DamageDealt()).append(" - ").append(b.getP2DamageDealt()).append("] ")
						.append(b.getP2().hasBeaten(b.getP1())).append(" ").append(b.getP2().getName()).append("\n");
			}
		}
		return results.toString();
	}

	public String postTournamentAwards() throws IndexOutOfBoundsException {
		String output = "";
		try {
			Player p1 = fetchHardestFoughtPlayer();
			Player p3 = fetchBiggestMilker();
			Player p4 = fetchHardestDoneBy();

			Collections.sort(players);
			output += "Congratulations to " + players.get(0).getName() + " on winning this tournament!\n";
			if (p1 != null) {
				output += "Props to " + p1.getName() + " for enduring the toughest range of opponents.\n";
			}
			if (p3 != null) {
				output += p3.getName()
						+ " can thank their lucky stars for being generally paired down the most considering their win rate.\n";
			}
			if (p4 != null) {
				output += "Commiserations to " + p4.getName() + " for being paired up unusually often.\n";
			}
			if (predictionsMade > 0) {
				output += "Of the " + predictionsMade + " match result predictions made, " + correctPredictions
						+ " were correct.\n";
			}
		} catch (IndexOutOfBoundsException e) {
			System.out.println("Exception thrown: Tried to access unavailable player.");
		}
		return output;
	}

	private Player fetchHardestDoneBy() {
		Collections.sort(players);
		Collections.reverse(players);
		for (Player p : players) {
			if (p.getOppWr() > 50 && !dropped.contains(p) && !p.isDropped()) {
				return p;
			}
		}
		return null;
	}

	private Player fetchBiggestMilker() {
		Collections.sort(players);
		for (Player p : players) {
			if (p.getOppWr() < 50 && !dropped.contains(p) && !p.isDropped() && !p.getName().equals("BYE")) {
				return p;
			}
		}
		return null;
	}

	private Player fetchHardestFoughtPlayer() {
		int highestOWR = 0;
		Player hardest = null;
		for (Player p : players) {
			if (p.getOppWr() > highestOWR && !p.getName().equals("BYE") && !p.isDropped()) {
				hardest = p;
				highestOWR = p.getOppWr();
			}
		}
		return hardest;
	}

	public String readInput() {
		return sc.nextLine();
	}

	public void postTourneyProcessing() {
		postString("FINAL STANDINGS");
		updateParticipantStats();
		postString(generateInDepthRankings(players));
		postString(postTournamentAwards());

		if (topCutThreshold > 1) {
			postString("Should we progress to the top cut of " + topCutThreshold + "? (y/n)");
			String input = readInput();
			if (input.toLowerCase().charAt(0) == 'y') {
				activeMetadataFile = activeMetadataFile.replace(".", "-topCut.");
				ArrayList<String> topCut = new ArrayList<>();
				for (int i = 0; i < topCutThreshold; i++) {
					topCut.add(players.get(i).getName());
				}
				players.clear();
				dropped.clear();
				for (String player : topCut) {
					addPlayer(player);
				}
				roundNumber = 1;
				numberOfRounds = logBase2(players.size());
				topCutThreshold = 0;
				for (int j = 0; j < players.size() / 2; j++) {
					currentBattles.add(new Battle(players.get(j), players.get(players.size() - (j + 1))));
				}
				assignTableNumbers(currentBattles);
				completedBattles.clear();
				run();
			} else {
				Utils.print("Thanks to everyone for taking part!");
			}
		}
	}

	public void run() {
		while (roundNumber <= getNumberOfRounds() && players.size() > 1) {
			Collections.shuffle(players);
			Utils.wipePane();
			updateParticipantStats();
			sortRankings();
			if (roundNumber == 1) {
				shufflePlayers();
			}
			generatePairings(0);
			save();
			sortRankings();
			Utils.print(generateInDepthRankings(players));
			pollForResults();

			roundNumber++;
		}
		save();
		Utils.wipePane();
		postTourneyProcessing();
	}

	void save() {
		tntFileManager.saveTournament();
	}

	public Player findPlayerByName(String s) {
		for (Player p : players) {
			if (p.getName().equals(s)) {
				return p;
			}
		}
		return null;
	}

	public Battle findBattleByName(String s) {
		for (Battle b : currentBattles) {
			if (b.getP1().getName().equals(s) || b.getP2().getName().equals(s)) {
				return b;
			}
		}
		return null;
	}

	public String getElo() {
		return elo;
	}

	public void setElo(String elo) {
		this.elo = elo;
	}

	public void eliminatePlayer(Player p) {
		dropped.add(p);
	}

	public void dropPlayer(String string) {
		if (getLivePlayerCount() > 2) {
			Player toDrop = findPlayerByName(string);
			toDrop.setDropped(true);
			dropped.add(toDrop);
			numberOfRounds = (logBase2(getLivePlayerCount()));
		} else {
			Utils.print("You can't drop a player when there are only 2, or less, remaining players.");
		}
	}

	public int getLivePlayerCount() {
		return players.size() - dropped.size();
	}

	public int getPredictionsMade() {
		return predictionsMade;
	}

	public int getCorrectPredictions() {
		return correctPredictions;
	}

	public String listAllDamageInCompletedGames() {
		StringBuilder result = new StringBuilder();
		for (Battle b : completedBattles) {
			result.append(b.toString());
		}
		if (result.length() > 0) {
			return result.substring(0, result.length() - 1);
		}
		return result.toString();
	}

	public void addBatchFromFile(String line) {
		String[] names = line.split(",");
		for (String s : names) {
			Player p = new Player(Utils.sanitise(s));
			players.add(p);
			postListOfConfirmedSignups();
		}
	}
}

class TntFileManager {

	static Tournament t;
	static String line;

	public TntFileManager(Tournament tournament) {
		t = tournament;
	}

	public void saveTournament() {

		if (!t.activeMetadataFile.equals("TournamentInProgress.tnt")) {
			String output = "";
			File file = new File(t.activeMetadataFile);

			output += "PLAYERS:\n";
			for (Player p : t.getPlayers()) {
				output += p.getName() + ",";
			}
			output = output.substring(0, output.length() - 1) + "\n";

			output += "VICTORIES:\n";
			for (Player p : t.getPlayers()) {
				output += p.getName() + "_" + p.getListOfNamesBeaten().toString() + "_"
						+ p.getListOfNamesPlayed().toString() + "\n";
			}
			output += "GAMES:\n";
			for (Battle b : t.currentBattles) {
				output += b.getP1().getName() + "," + b.getP2().getName() + "," + b.getTableNumber() + "\n";
			}
			output += "PROPERTIES:\n";
			output += "On Round:" + t.roundNumber + "\n";
			output += "numberOfRounds:" + t.numberOfRounds + "\n";
			output += "topCut:" + t.getTopCutThreshold() + "\n";
			output += "ELO:" + t.getElo() + "\n";
			output += "predictionsMade:" + t.getPredictionsMade() + "\n";
			output += "correctPredictions:" + t.getCorrectPredictions() + "\n";
			output += "damageDealtAndTaken:" + t.listAllDamageInCompletedGames() + "\n";
			StringBuilder s = new StringBuilder();
			for (Player p : t.getDroppedPlayers()) {
				s.append(p.getName());
				s.append(",");
			}
			if (s.length() > 0) {
				s = new StringBuilder(s.substring(0, s.length() - 1));
			}
			output += "Dropped:" + s + "\n";
			try {
				PrintWriter writer = new PrintWriter(file, "UTF-8");
				writer.print(output);
				writer.close();
			} catch (FileNotFoundException e) {
				Utils.print("Couldn't write file.");
			} catch (UnsupportedEncodingException e) {
				Utils.print("Unsupported encoding.");
			}
		}
	}

	public static void loadTournament(Tournament t, String fileName) throws IOException {
		t.getPlayers().clear();
		t.currentBattles.clear();
		t.activeMetadataFile = fileName;
		BufferedReader br = new BufferedReader(new FileReader(t.activeMetadataFile));
		try {
			line = br.readLine();
			if (line.contains("PLAYERS")) {
				line = br.readLine();
				while (!line.contains("VICTORIES")) {
					t.addBatchFromFile(line);
					line = br.readLine();
				}
				line = br.readLine();
				while (!line.contains("GAMES")) {
					addGamesToPlayerHistory(line);
					line = br.readLine();
				}
				line = br.readLine();
				while (!line.contains("PROPERTIES")) {
					t.currentBattles.add(parseLineToBattle(line));
					line = br.readLine();
				}
				line = br.readLine();
				while (line != null) {
					parseProperties(line);
					line = br.readLine();
				}
			}
		} catch (IOException e) {
			Utils.print("Error reading supplied file, starting at line: \"" + line + "\"");
		}

		finally {
			br.close();
		}
		t.updateParticipantStats();
	}

	public static void parseProperties(String line2) {
		try {
			String[] propertyPair = line.split(":");
			switch (propertyPair[0].toLowerCase()) {

			case "on round":
				t.roundNumber = Integer.parseInt(propertyPair[1]);
				break;
			case "numberofrounds":
				t.numberOfRounds = Integer.parseInt(propertyPair[1]);
				break;
			case "topcut":
				int tC = Integer.parseInt(propertyPair[1]);
				if (tC < t.getPlayers().size()) {
					t.setTopCut(tC);
				}
				break;
			case "elo":
				if (propertyPair[1].equals("on")) {
					t.setElo("on");
				}
				break;
			case "dropped":
				String[] droppedNames = propertyPair[1].split(",");
				for (String s : droppedNames) {
					t.getDroppedPlayers().add(t.findPlayerByName(s));
				}
				for (Player p : t.getDroppedPlayers()) {
					p.setDropped(true);
				}
				break;
			case "correctpredictions":
				t.correctPredictions = Integer.parseInt(propertyPair[1]);
				break;
			case "predictionsmade":
				t.predictionsMade = Integer.parseInt(propertyPair[1]);
				break;
			case "damagedealtandtaken":
				String[] listOfAllCompletedGames = propertyPair[1].split("/");
				for (String s : listOfAllCompletedGames) {
					String[] playersAndDamage = s.split(";");
					String[] players = playersAndDamage[0].split(",");
					String[] damage = playersAndDamage[1].split(",");
					Player p1 = t.findPlayerByName(players[0]);
					Player p2 = t.findPlayerByName(players[1]);
					Battle b = new Battle(p1, p2, Integer.parseInt(damage[0]), Integer.parseInt(damage[1]));
					t.completedBattles.add(b);
				}
				break;
			default:
				break;
			}
		} catch (Exception e) {
			Utils.print("Error reading supplied file, starting at line: \"" + line + "\".");
		}
	}

	static Battle parseLineToBattle(String line) {
		String[] currentCombatants = line.split(",");
		Player p1 = t.findPlayerByName(currentCombatants[0]);
		Player p2 = t.findPlayerByName(currentCombatants[1]);
		String tableNumber = currentCombatants[2];
		Battle b = new Battle(p1, p2);
		b.setTableNumber(Integer.parseInt(tableNumber));
		return b;
	}

	public static void addGamesToPlayerHistory(String line) {
		try {
			String[] information = line.split("_");
			Player p = t.findPlayerByName(information[0]);

			String hasBeaten = information[1];
			hasBeaten = hasBeaten.replaceAll("\\[", "");
			hasBeaten = hasBeaten.replaceAll("]", "");
			String[] playersBeaten = hasBeaten.split(",");
			for (String s : playersBeaten) {
				if (s.length() > 0) {
					p.addToListOfVictories(t.findPlayerByName(Utils.trimWhitespace(s)));
				}
			}

			String hasPlayed = information[2];
			hasPlayed = hasPlayed.replaceAll("\\[", "");
			hasPlayed = hasPlayed.replaceAll("]", "");
			String[] playersPlayed = hasPlayed.split(",");
			for (String s : playersPlayed) {
				if (s.length() > 0) {
					p.addToListOfPlayed(t.findPlayerByName(Utils.trimWhitespace(s)));
				}
			}
		} catch (Exception e) {
			Utils.print("Error reading supplied file, starting at line: \"" + line + "\".");
		}
	}

}

class Battle implements Comparable<Battle> {

	Player p1;
	Player p2;
	int tableNumber;
	public boolean wasSeeded = false;
	private int p1DamageDealt = 0;
	private int p2DamageDealt = 0;

	public String toString() {
		return p1.getName() + "," + p2.getName() + ";" + getP1DamageDealt() + "," + getP2DamageDealt() + "/";
	}

	public Battle(Player myP1, Player myP2) {
		p1 = myP1;
		p2 = myP2;
	}

	public Battle(Player myP1, Player myP2, int i, int j) {
		p1 = myP1;
		p2 = myP2;
		setP1DamageDealt(i);
		setP2DamageDealt(j);
	}

	public Player getP1() {
		return p1;
	}

	public Player getP2() {
		return p2;
	}

	public void setPlayer(String s, Player p) {
		if (s.contains("1")) {
			this.p1 = p;
		} else {
			this.p2 = p;
		}
	}

	public void setTableNumber(int tN) {
		tableNumber = tN;
	}

	public int getTableNumber() {
		return tableNumber;
	}

	public boolean contains(Player winner) {
		return p1 == winner || p2 == winner;
	}

	public int getElo(Player p) {
		Player opponent = otherPlayer(p);
		if (p.getName().equals("BYE")) {
			return 0;
		}
		if (opponent.getName().equals("BYE")) {
			return 100;
		}
		float ourEloScore = (p.getOppWr() * p.getScore()) + 1;
		float theirEloScore = (opponent.getOppWr() * opponent.getScore()) + 1;
		float ourElo = 1;
		double power = (theirEloScore - ourEloScore) / 400;
		power = Math.pow(10, power);
		ourElo += power;
		ourElo = 1 / ourElo;
		ourElo *= 100;
		ourElo = Math.round(ourElo);

		return (int) ourElo;
	}

	public Player otherPlayer(Player p) {
		if (p.equals(p1)) {
			return p2;
		}
		return p1;
	}

	@Override
	public int compareTo(Battle compareTo) {
		if (this.shoeInFactor() >= compareTo.shoeInFactor()) {
			return -1;
		}
		return 1;
	}

	private int shoeInFactor() {
		return Math.abs(getElo(p1) - getElo(p2));
	}

	public boolean contains(String name) {
		return p1.getName().toLowerCase().contains(name) || p2.getName().toLowerCase().contains(name);
	}

	public int getP1DamageDealt() {
		return p1DamageDealt;
	}

	public void setP1DamageDealt(int newP1DamageDealt) {
		this.p1DamageDealt = newP1DamageDealt;
	}

	public int getP2DamageDealt() {
		return p2DamageDealt;
	}

	public void setP2DamageDealt(int newP2DamageDealt) {
		this.p2DamageDealt = newP2DamageDealt;
	}

}

class PlayerCreator {

	private final Tournament t;

	public PlayerCreator(Tournament tourney) {
		t = tourney;
	}

	public void capturePlayers() {
		Utils.wipePane();
		while (!t.allParticipantsIn) {
			int playerNumbers = (t.getPlayers().size() + (t.currentBattles.size() * 2));
			if (t.findPlayerByName("BYE") != null) {
				playerNumbers--;
			}

			Utils.print("Enter the name of the next participant, or participants separated by commas. ");
			Utils.print("While registering players, you can enter 'drop' to remove a player before beginning.");
			Utils.print("Enter 'done' to begin.");
			Utils.print("Current Participants: " + playerNumbers + "  Rounds required: "
					+ t.logBase2(t.getPlayers().size()));
			Utils.print("");
			String input = t.readInput();
			processPlayerName(input);
		}
	}

	public void processPlayerName(String input) throws NumberFormatException {
		try {
			switch (input.toLowerCase()) {
			case "drop":
				Utils.print("Enter the player number (as shown below) of the player you'd like to remove.");
				String dropMe = t.readInput();
				dropPlayerByIndex(dropMe);
				break;
			case "done":
				t.allParticipantsIn = true;
				break;
			default:
				if (input.contains(",")) {
					t.addBatch(input);
				} else {
					t.addPlayer(input);
				}
				break;
			}
		} catch (NumberFormatException nfe) {
			Utils.print("Illegal input - that's not a usable player index.");
		}
	}

	private void dropPlayerByIndex(String dropMe) {
		int index = Integer.parseInt(dropMe);
		index--;
		if (index >= 0 && index < t.getPlayers().size()) {
			if (t.getPlayers().get(index).getName().equals("BYE")) {
				Utils.print("You can't drop the Bye. Either remove a real player to remove it, or add another player to overwrite it.");
			} else {
				Player p = t.getPlayers().remove(index);
				t.getPlayers().remove(t.findPlayerByName("BYE"));
				Utils.print("Removed " + p.getName() + ".");

			}
		} else {
			Utils.print("Player at index " + dropMe + 1 + " does not exist.");
		}
		t.postListOfConfirmedSignups();
	}
}

class Player implements Comparable<Player> {

	String name;
	private String winPattern;
	int score = 0;
	int oppWr = 0;
	public double damageDealt = 0;
	private double damageReceived = 0;
	public int lastDocumentedPosition = 0;
	public boolean isDropped = false;
	public ArrayList<Player> previousRounds = new ArrayList<>();
	ArrayList<Player> victories = new ArrayList<>();

	public Player(String string) {
		String sanitisedName = string.replace("/", "-");
		name = sanitisedName;
	}

	public double getDamageReceived() {
		return damageReceived;
	}

	public void updatePositionInRankings(ArrayList<Player> players) {
		for (int i = 0; i < players.size(); i++) {
			if (this == players.get(i)) {
				lastDocumentedPosition = i + 1;
			}
		}
	}

	public void updateWinPattern() {
		setWinPattern("");
		for (Player p : previousRounds) {
			if (victories.contains(p)) {
				setWinPattern(getWinPattern() + "W");
			} else {
				if (!p.victories.contains(this)) {
					setWinPattern(getWinPattern() + "T");
				} else {
					setWinPattern(getWinPattern() + "L");
				}
			}
		}
	}

	public void recalculateOppWr() {
		int maxWinsPossible = 0;
		for (Player p : previousRounds) {
			maxWinsPossible += p.getListOfNamesPlayed().size();
		}
		int winsActuallyAttained = 0;
		for (Player p : previousRounds) {
			winsActuallyAttained += p.getListOfNamesBeaten().size();
		}
		if (maxWinsPossible > 0) {
			oppWr = (100 * winsActuallyAttained) / maxWinsPossible;
		} else {
			oppWr = 0;
		}
	}

	@Override
	public int compareTo(Player p) {
		if (this.lastDocumentedPosition == 0 && p.lastDocumentedPosition == 0) {
			if (this.name.compareTo(p.name) < 0) {
				return -1;
			}
			return 1;
		} else if (this.name.equals("BYE")) {
			return 1;
		} else if (p.getName().equals("BYE")) {
			return -1;
		} else if (this.score > p.getScore()) {
			return -1;
		} else if (this.score < p.getScore()) {
			return 1;
		} else if (this.oppWr > p.getOppWr()) {
			return -1;
		} else if (this.oppWr < p.getOppWr()) {
			return 1;
		} else if (this.getAverageDamageReceived() < p.getAverageDamageReceived()) {
			return -1;
		} else if (this.getAverageDamageReceived() > p.getAverageDamageReceived()) {
			return 1;
		} else if (this.getAverageDamageDealt() > p.getAverageDamageDealt()) {
			return -1;
		} else if (this.getAverageDamageDealt() < p.getAverageDamageDealt()) {
			return 1;
		} else if (this.getDamageReceived() < p.getDamageReceived()) {
			return -1;
		} else if (this.getDamageReceived() > p.getDamageReceived()) {
			return 1;
		}
		return 0;
	}

	public double getAverageDamageDealt() {
		DecimalFormat df = new DecimalFormat("#.##");
		df.setRoundingMode(RoundingMode.CEILING);
		if (previousRounds.size() > 0) {
			return (damageDealt / (previousRounds.size()));
		}
		return 0.0;
	}

	public double getAverageDamageReceived() {
		DecimalFormat df = new DecimalFormat("#.##");
		df.setRoundingMode(RoundingMode.CEILING);
		if (previousRounds.size() > 0) {
			return (damageReceived / previousRounds.size());
		}
		return 0.0;
	}

	public int getScore() {
		return score;
	}

	public String getName() {
		return name;
	}

	public void beats(Player p2) {
		this.logOpponent(p2);
		p2.logOpponent(this);
		this.victories.add(p2);
		this.recalculateScore();
		p2.recalculateScore();
	}

	public void tied(Player p2) {
		this.logOpponent(p2);
		p2.logOpponent(this);
		this.recalculateScore();
		p2.recalculateScore();
	}

	private void logOpponent(Player foe) {
		this.previousRounds.add(foe);
	}

	public ArrayList<Player> getOpponentsList() {
		return previousRounds;
	}

	public ArrayList<Player> getListOfVictories() {
		return victories;
	}

	public int getOppWr() {
		return oppWr;
	}

	public int getPositionInRankings() {
		return lastDocumentedPosition;
	}

	public void setName(String newName) {
		String sanitisedName = newName.replace("/", "-");
		this.name = sanitisedName;
	}

	public void recalculateScore() {
		score = (3 * victories.size());
	}

	public ArrayList<String> getListOfNamesPlayed() {
		ArrayList<String> namesPlayed = new ArrayList<>();
		for (Player p : getOpponentsList()) {
			namesPlayed.add(p.getName());
		}
		return namesPlayed;
	}

	public ArrayList<String> getListOfNamesBeaten() {
		ArrayList<String> namesBeaten = new ArrayList<>();
		for (Player p : getListOfVictories()) {
			namesBeaten.add(p.getName());
		}
		return namesBeaten;
	}

	public void addToListOfVictories(Player beaten) {
		victories.add(beaten);
	}

	public void addToListOfPlayed(Player played) {
		previousRounds.add(played);
	}

	private void recalculateDamageDealt(ArrayList<Battle> bs) {
		damageDealt = 0;
		for (Battle b : bs) {
			if (b.getP1() == this) {
				damageDealt += b.getP1DamageDealt();
			} else if (b.getP2() == this) {
				damageDealt += b.getP2DamageDealt();
			}
		}
	}

	private void recalculateDamageReceived(ArrayList<Battle> bs) {
		damageReceived = 0;
		for (Battle b : bs) {
			if (b.getP1() == this) {
				damageReceived += b.getP2DamageDealt();
			} else if (b.getP2() == this) {
				damageReceived += b.getP1DamageDealt();
			}
		}
	}

	public String getWinPattern() {
		return winPattern;
	}

	public void setWinPattern(String winPattern) {
		this.winPattern = winPattern;
	}

	public void updateParticipantStats(ArrayList<Battle> bs) {
		recalculateScore();
		recalculateOppWr();
		recalculateDamageDealt(bs);
		recalculateDamageReceived(bs);
		updateWinPattern();
	}

	public void setDropped(boolean b) {
		isDropped = b;
	}

	public boolean isDropped() {
		return isDropped;
	}

	public String hasBeaten(Player p2) {
		if (this.getName().equals("BYE")) {
			return "(L)";
		}
		if (p2.getName().equals("BYE")) {
			return "(W)";
		}
		if (this.getListOfNamesPlayed().contains(p2.getName()) && p2.getListOfNamesPlayed().contains(this.getName())
				&& !this.getListOfNamesBeaten().contains(p2.getName())
				&& !p2.getListOfNamesBeaten().contains(this.getName())) {
			return "(T)";
		}
		if (this.getListOfNamesBeaten().contains(p2.getName()) && !p2.getListOfNamesBeaten().contains(this.getName())) {
			return "(W)";
		}
		if (!this.getListOfNamesBeaten().contains(p2.getName()) && p2.getListOfNamesBeaten().contains(this.getName())) {
			return "(L)";
		}
		return "(?)";
	}
}

class Utils {

	public static void wipePane() {
		for (int clear = 0; clear < 40; clear++) {
			System.out.println("");
		}
	}
	
	public static void handleBattleWinner(Battle b, String whichPlayerWon) {
		switch (whichPlayerWon) {
		case "1":
			b.getP1().beats(b.getP2());
			break;
		case "2":
			b.getP2().beats(b.getP1());
			break;
		case "0":
			b.getP1().tied(b.getP2());
			break;
		}
	}

	public static void postString(String string) {
		System.out.println(string);
	}

	public static String trimWhitespace(String s) {
		if (s.length() == 0) {
			return s;
		}
		if (s.charAt(0) == ' ' || s.charAt(0) == '\t') {
			return trimWhitespace(s.substring(1));
		}
		if (s.charAt(s.length() - 1) == ' ' || s.charAt(s.length() - 1) == '\t') {
			return trimWhitespace(s.substring(0, s.length() - 1));
		}
		return s;
	}

	public static void print(String string) {
		System.out.println(string);
	}

	public static String rpad(String inStr, int finalLength) {
		return (inStr
				+ "                                                                                                                          ")
						.substring(0, finalLength);
	}

	public static String sanitise(String sanitiseThis) {
		String processedName = sanitiseThis;
		processedName = processedName.replaceAll("À", "A");
		processedName = processedName.replaceAll("à", "a");
		processedName = processedName.replaceAll("Á", "A");
		processedName = processedName.replaceAll("á", "a");
		processedName = processedName.replaceAll("Â", "A");
		processedName = processedName.replaceAll("â", "a");
		processedName = processedName.replaceAll("Ã", "A");
		processedName = processedName.replaceAll("ã", "a");
		processedName = processedName.replaceAll("Ä", "A");
		processedName = processedName.replaceAll("ä", "a");
		processedName = processedName.replaceAll("Ç", "C");
		processedName = processedName.replaceAll("ç", "c");
		processedName = processedName.replaceAll("È", "E");
		processedName = processedName.replaceAll("è", "e");
		processedName = processedName.replaceAll("É", "E");
		processedName = processedName.replaceAll("é", "e");
		processedName = processedName.replaceAll("Ê", "E");
		processedName = processedName.replaceAll("ê", "e");
		processedName = processedName.replaceAll("Ë", "E");
		processedName = processedName.replaceAll("ë", "e");
		processedName = processedName.replaceAll("Ì", "I");
		processedName = processedName.replaceAll("ì", "i");
		processedName = processedName.replaceAll("Í", "I");
		processedName = processedName.replaceAll("í", "i");
		processedName = processedName.replaceAll("Î", "I");
		processedName = processedName.replaceAll("î", "i");
		processedName = processedName.replaceAll("Ï", "I");
		processedName = processedName.replaceAll("ï", "i");
		processedName = processedName.replaceAll("Ñ", "N");
		processedName = processedName.replaceAll("ñ", "n");
		processedName = processedName.replaceAll("Ò", "O");
		processedName = processedName.replaceAll("ò", "o");
		processedName = processedName.replaceAll("Ó", "O");
		processedName = processedName.replaceAll("ó", "o");
		processedName = processedName.replaceAll("Ô", "O");
		processedName = processedName.replaceAll("ô", "o");
		processedName = processedName.replaceAll("Õ", "O");
		processedName = processedName.replaceAll("õ", "o");
		processedName = processedName.replaceAll("Ö", "O");
		processedName = processedName.replaceAll("ö", "o");
		processedName = processedName.replaceAll("Š", "S");
		processedName = processedName.replaceAll("š", "s");
		processedName = processedName.replaceAll("Ú", "U");
		processedName = processedName.replaceAll("ù", "u");
		processedName = processedName.replaceAll("Û", "U");
		processedName = processedName.replaceAll("ú", "u");
		processedName = processedName.replaceAll("Ü", "U");
		processedName = processedName.replaceAll("û", "u");
		processedName = processedName.replaceAll("Ù", "U");
		processedName = processedName.replaceAll("ü", "u");
		processedName = processedName.replaceAll("Ý", "Y");
		processedName = processedName.replaceAll("ý", "y");
		processedName = processedName.replaceAll("Ÿ", "Y");
		processedName = processedName.replaceAll("ÿ", "y");
		processedName = processedName.replaceAll("Ž", "Z");
		processedName = processedName.replaceAll("ž", "z");
		return trimWhitespace(processedName);
	}

	public static Player getRandomPlayer(Tournament t) {
		Random r = new Random();
		int rand = r.nextInt(t.getPlayers().size());
		Player p = t.getPlayers().get(rand);
		if (p.isDropped() || p.getName().equals("BYE")) {
			return getRandomPlayer(t);
		}
		return p;
	}

}
