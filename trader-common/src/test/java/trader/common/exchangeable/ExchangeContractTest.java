package trader.common.exchangeable;

import org.junit.Test;

public class ExchangeContractTest {

    @Test
    public void test() {
        ExchangeContract contract = ExchangeContract.matchContract(Exchange.CFFEX, "T");

    }

}
