/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package co.rsk.net.handler.txvalidator;

import org.ethereum.core.AccountState;
import org.ethereum.core.Transaction;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.math.BigInteger;

public class TxValidatorAccountBalanceValidatorTest {

    @Test
    public void validAccountBalance() {
        Transaction tx1 = Mockito.mock(Transaction.class);
        Transaction tx2 = Mockito.mock(Transaction.class);
        Transaction tx3 = Mockito.mock(Transaction.class);
        AccountState as = Mockito.mock(AccountState.class);

        Mockito.when(tx1.getGasLimitAsInteger()).thenReturn(BigInteger.valueOf(1));
        Mockito.when(tx2.getGasLimitAsInteger()).thenReturn(BigInteger.valueOf(1));
        Mockito.when(tx3.getGasLimitAsInteger()).thenReturn(BigInteger.valueOf(2));
        Mockito.when(tx1.getGasPriceAsInteger()).thenReturn(BigInteger.valueOf(1));
        Mockito.when(tx2.getGasPriceAsInteger()).thenReturn(BigInteger.valueOf(10000));
        Mockito.when(tx3.getGasPriceAsInteger()).thenReturn(BigInteger.valueOf(5000));
        Mockito.when(as.getBalance()).thenReturn(BigInteger.valueOf(10000));

        TxValidatorAccountBalanceValidator tvabv = new TxValidatorAccountBalanceValidator();

        Assert.assertTrue(tvabv.validate(tx1, as, null, null, Long.MAX_VALUE));
        Assert.assertTrue(tvabv.validate(tx2, as, null, null, Long.MAX_VALUE));
        Assert.assertTrue(tvabv.validate(tx3, as, null, null, Long.MAX_VALUE));
    }

    @Test
    public void invalidAccountBalance() {
        Transaction tx1 = Mockito.mock(Transaction.class);
        Transaction tx2 = Mockito.mock(Transaction.class);
        AccountState as = Mockito.mock(AccountState.class);

        Mockito.when(tx1.getGasLimitAsInteger()).thenReturn(BigInteger.valueOf(1));
        Mockito.when(tx2.getGasLimitAsInteger()).thenReturn(BigInteger.valueOf(2));
        Mockito.when(tx1.getGasPriceAsInteger()).thenReturn(BigInteger.valueOf(20));
        Mockito.when(tx2.getGasPriceAsInteger()).thenReturn(BigInteger.valueOf(10));
        Mockito.when(as.getBalance()).thenReturn(BigInteger.valueOf(19));

        TxValidatorAccountBalanceValidator tvabv = new TxValidatorAccountBalanceValidator();

        Assert.assertFalse(tvabv.validate(tx1, as, null, null, Long.MAX_VALUE));
        Assert.assertFalse(tvabv.validate(tx2, as, null, null, Long.MAX_VALUE));
    }

}
