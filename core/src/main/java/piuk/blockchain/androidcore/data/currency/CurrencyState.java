package piuk.blockchain.androidcore.data.currency;

import piuk.blockchain.androidcore.utils.PrefsUtil;

public class CurrencyState {

    private static CurrencyState INSTANCE;

    private PrefsUtil prefs;
    private CryptoCurrencies cryptoCurrency;
    private boolean isDisplayingCryptoCurrency;

    private CurrencyState() {
        isDisplayingCryptoCurrency = false;
    }

    public static CurrencyState getInstance() {
        if (INSTANCE == null)
            INSTANCE = new CurrencyState();
        return INSTANCE;
    }

    public void init(PrefsUtil prefs) {
        this.prefs = prefs;
        String value = prefs.getValue(PrefsUtil.KEY_CURRENCY_CRYPTO_STATE, CryptoCurrencies.BTC.name());
        cryptoCurrency = CryptoCurrencies.valueOf(value);
        isDisplayingCryptoCurrency = true;
    }

    public CryptoCurrencies getCryptoCurrency() {
        return cryptoCurrency;
    }

    public void setCryptoCurrency(CryptoCurrencies cryptoCurrency) {
        prefs.setValue(PrefsUtil.KEY_CURRENCY_CRYPTO_STATE, cryptoCurrency.name());
        this.cryptoCurrency = cryptoCurrency;
    }

    public void toggleCryptoCurrency() {
        if (cryptoCurrency == CryptoCurrencies.BTC) {
            cryptoCurrency = CryptoCurrencies.ETHER;
        } else {
            cryptoCurrency = CryptoCurrencies.BTC;
        }

        setCryptoCurrency(cryptoCurrency);

    }

    public void toggleDisplayingCrypto() {
        isDisplayingCryptoCurrency = !isDisplayingCryptoCurrency;
    }

    public boolean isDisplayingCryptoCurrency() {
        return isDisplayingCryptoCurrency;
    }

    public void setDisplayingCryptoCurrency(boolean displayingCryptoCurrency) {
        isDisplayingCryptoCurrency = displayingCryptoCurrency;
    }

    public String getFiatUnit() {
        return prefs.getValue(PrefsUtil.KEY_SELECTED_FIAT, PrefsUtil.DEFAULT_CURRENCY);
    }
}
