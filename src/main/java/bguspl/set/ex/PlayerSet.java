package bguspl.set.ex;
import bguspl.set.Env;

public class PlayerSet {
    //A class that defines a player set: it holds the slots, cards, and player id.
    private final int playerId;
    private final int[] setSlots;
    private final int[] setCards;
    
    public PlayerSet(int playerId, int[] setSlots, int[] setCards) {
        this.playerId = playerId;
        this.setSlots = setSlots;
        this.setCards = setCards;
    }

    public int getPlayerId() {
        return this.playerId;
    }
    public int[] getSetSlots() {
        return this.setSlots;
    }
    public int[] getSetCards(){
        return this.setCards;
    }
}
