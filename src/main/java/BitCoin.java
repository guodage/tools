import java.math.BigDecimal;
import java.math.MathContext;

/**
 * Created by guodage on 2017/4/26.
 */
public class BitCoin {


    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("用法 bit <price>");
            return;
        }
        double in = Double.valueOf(args[0]);

        MathContext mc = new MathContext(7);
        BigDecimal point = new BigDecimal(1.004005).multiply(new BigDecimal(in), mc);
        System.out.println("盈利为0的价位 " + point);
    }


}
