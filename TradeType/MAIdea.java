package TradeType;

import utility.Utility;

import java.time.LocalDateTime;

public class MAIdea {

    private final LocalDateTime tradeTime;
    private final double tradePrice;
    private final int size;
    private final String comments;

    public MAIdea(LocalDateTime t, double p, int q) {
        tradeTime = t;
        tradePrice = p;
        size = q;
        comments = "";
    }

    public MAIdea(LocalDateTime t, double p, int q, String c) {
        tradeTime = t;
        tradePrice = p;
        size = q;
        comments = c;
    }

    public LocalDateTime getIdeaTime() {
        return tradeTime;
    }

    public double getIdeaPrice() {
        return tradePrice;
    }

    public int getIdeaSize() {
        return size;
    }


    @Override
    public String toString() {
        return Utility.str(" MAIdea", tradeTime, size > 0 ? "BUY" : "SELL", size
                , " @ ", Math.round(100d * tradePrice) / 100d, "comments ", comments);
    }
}
