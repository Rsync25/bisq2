/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.desktop.main.content.bisq_easy.take_offer.review;

import bisq.bonded_roles.market_price.MarketPrice;
import bisq.bonded_roles.market_price.MarketPriceService;
import bisq.chat.ChatChannelDomain;
import bisq.chat.ChatChannelSelectionService;
import bisq.chat.ChatService;
import bisq.chat.bisqeasy.open_trades.BisqEasyOpenTradeChannelService;
import bisq.common.currency.Market;
import bisq.common.monetary.Monetary;
import bisq.common.monetary.PriceQuote;
import bisq.contract.bisq_easy.BisqEasyContract;
import bisq.desktop.ServiceProvider;
import bisq.desktop.common.threading.UIThread;
import bisq.desktop.common.view.Controller;
import bisq.desktop.common.view.NavigationTarget;
import bisq.desktop.components.overlay.Popup;
import bisq.desktop.main.content.bisq_easy.components.PriceInput;
import bisq.desktop.main.content.bisq_easy.components.ReviewDataDisplay;
import bisq.i18n.Res;
import bisq.offer.Direction;
import bisq.offer.amount.OfferAmountUtil;
import bisq.offer.amount.spec.FixedAmountSpec;
import bisq.offer.bisq_easy.BisqEasyOffer;
import bisq.offer.payment_method.FiatPaymentMethodSpec;
import bisq.offer.price.PriceUtil;
import bisq.offer.price.spec.FloatPriceSpec;
import bisq.offer.price.spec.MarketPriceSpec;
import bisq.offer.price.spec.PriceSpec;
import bisq.presentation.formatters.AmountFormatter;
import bisq.presentation.formatters.PercentageFormatter;
import bisq.presentation.formatters.PriceFormatter;
import bisq.trade.TradeException;
import bisq.trade.bisq_easy.BisqEasyTrade;
import bisq.trade.bisq_easy.BisqEasyTradeService;
import bisq.user.banned.BannedUserService;
import bisq.user.identity.UserIdentity;
import bisq.user.identity.UserIdentityService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class TakeOfferReviewController implements Controller {
    private final TakeOfferReviewModel model;
    @Getter
    private final TakeOfferReviewView view;
    private final BisqEasyOpenTradeChannelService bisqEasyOpenTradeChannelService;
    private final ChatService chatService;
    private final Consumer<NavigationTarget> closeAndNavigateToHandler;
    private final Consumer<Boolean> mainButtonsVisibleHandler;
    private final PriceInput priceInput;
    private final MarketPriceService marketPriceService;
    private final UserIdentityService userIdentityService;
    private final BisqEasyTradeService bisqEasyTradeService;
    private final BannedUserService bannedUserService;
    private final ReviewDataDisplay reviewDataDisplay;

    public TakeOfferReviewController(ServiceProvider serviceProvider,
                                     Consumer<Boolean> mainButtonsVisibleHandler,
                                     Consumer<NavigationTarget> closeAndNavigateToHandler) {
        this.mainButtonsVisibleHandler = mainButtonsVisibleHandler;
        userIdentityService = serviceProvider.getUserService().getUserIdentityService();
        chatService = serviceProvider.getChatService();
        this.closeAndNavigateToHandler = closeAndNavigateToHandler;
        bisqEasyOpenTradeChannelService = chatService.getBisqEasyOpenTradeChannelService();
        marketPriceService = serviceProvider.getBondedRolesService().getMarketPriceService();
        bisqEasyTradeService = serviceProvider.getTradeService().getBisqEasyTradeService();
        bannedUserService = serviceProvider.getUserService().getBannedUserService();

        priceInput = new PriceInput(serviceProvider.getBondedRolesService().getMarketPriceService());
        reviewDataDisplay = new ReviewDataDisplay();
        model = new TakeOfferReviewModel();
        view = new TakeOfferReviewView(model, this, reviewDataDisplay.getRoot());
    }

    public void init(BisqEasyOffer bisqEasyOffer) {
        model.setBisqEasyOffer(bisqEasyOffer);
        Market market = bisqEasyOffer.getMarket();
        priceInput.setMarket(market);

        String marketCodes = market.getMarketCodes();
        priceInput.setDescription(Res.get("bisqEasy.takeOffer.review.price.price", marketCodes));

        if (bisqEasyOffer.getAmountSpec() instanceof FixedAmountSpec) {
            OfferAmountUtil.findBaseSideFixedAmount(marketPriceService, bisqEasyOffer)
                    .ifPresent(model::setTakersBaseSideAmount);
            OfferAmountUtil.findQuoteSideFixedAmount(marketPriceService, bisqEasyOffer)
                    .ifPresent(model::setTakersQuoteSideAmount);
        }

        Direction direction = bisqEasyOffer.getTakersDirection();
        if (direction.isBuy()) {
            // If taker is buyer we set the sellers price from the offer
            PriceSpec priceSpec = bisqEasyOffer.getPriceSpec();
            model.setSellersPriceSpec(priceSpec);

            Optional<PriceQuote> priceQuote = PriceUtil.findQuote(marketPriceService, bisqEasyOffer);
            priceQuote.ifPresent(priceInput::setQuote);

            applyPriceQuote(priceQuote);
            applyPriceDetails(priceSpec, market);
        }

        model.setFee(direction.isBuy() ?
                Res.get("bisqEasy.takeOffer.review.fee.buyer") :
                Res.get("bisqEasy.takeOffer.review.fee.seller"));

        model.setFeeDetails(direction.isBuy() ?
                Res.get("bisqEasy.takeOffer.review.feeDetails.buyer") :
                Res.get("bisqEasy.takeOffer.review.feeDetails.seller"));
    }


    public void setTradePriceSpec(PriceSpec priceSpec) {
        // Only handle if taker is seller
        if (priceSpec != null && model.getBisqEasyOffer() != null && model.getBisqEasyOffer().getTakersDirection().isSell()) {
            model.setSellersPriceSpec(priceSpec);

            Optional<PriceQuote> priceQuote = PriceUtil.findQuote(marketPriceService, priceSpec, model.getBisqEasyOffer().getMarket());
            priceQuote.ifPresent(priceInput::setQuote);

            applyPriceQuote(priceQuote);
            applyPriceDetails(priceSpec, model.getBisqEasyOffer().getMarket());

            OfferAmountUtil.findBaseSideMinOrFixedAmount(marketPriceService, model.getBisqEasyOffer().getAmountSpec(), priceSpec, model.getBisqEasyOffer().getMarket())
                    .ifPresent(model::setTakersBaseSideAmount);
        }
    }

    public void setTakersBaseSideAmount(Monetary amount) {
        if (amount != null) {
            model.setTakersBaseSideAmount(amount);
        }
    }

    public void setTakersQuoteSideAmount(Monetary amount) {
        if (amount != null) {
            model.setTakersQuoteSideAmount(amount);
        }
    }

    public void setFiatPaymentMethodSpec(FiatPaymentMethodSpec spec) {
        if (spec != null) {
            model.setFiatPaymentMethodSpec(spec);
            String displayString = spec.getDisplayString();
            model.setPaymentMethod(displayString);
        }
    }

    public void doTakeOffer() {
        try {
            UserIdentity myUserIdentity = checkNotNull(userIdentityService.getSelectedUserIdentity());
            if (bannedUserService.isNetworkIdBanned(model.getBisqEasyOffer().getMakerNetworkId()) ||
                    bannedUserService.isUserProfileBanned(myUserIdentity.getUserProfile())) {
                return;
            }
            BisqEasyOffer bisqEasyOffer = model.getBisqEasyOffer();
            BisqEasyTrade bisqEasyTrade = bisqEasyTradeService.onTakeOffer(myUserIdentity.getIdentity(),
                    bisqEasyOffer,
                    model.getTakersBaseSideAmount(),
                    model.getTakersQuoteSideAmount(),
                    bisqEasyOffer.getBaseSidePaymentMethodSpecs().get(0),
                    model.getFiatPaymentMethodSpec());

            model.setBisqEasyTrade(bisqEasyTrade);

            BisqEasyContract contract = bisqEasyTrade.getContract();
            String tradeId = bisqEasyTrade.getId();
            bisqEasyOpenTradeChannelService.sendTakeOfferMessage(tradeId, bisqEasyOffer, contract.getMediator())
                    .thenAccept(result -> UIThread.run(() -> {
                        ChatChannelSelectionService chatChannelSelectionService = chatService.getChatChannelSelectionService(ChatChannelDomain.BISQ_EASY_OFFERBOOK);
                        bisqEasyOpenTradeChannelService.findChannel(tradeId)
                                .ifPresent(chatChannelSelectionService::selectChannel);
                        model.getShowTakeOfferSuccess().set(true);
                        mainButtonsVisibleHandler.accept(false);
                    }));
        } catch (TradeException e) {
            new Popup().error(e).show();
        }
    }

    @Override
    public void onActivate() {
        String toSendAmountDescription, toSendAmount, toSendCode, toReceiveAmountDescription, toReceiveAmount, toReceiveCode;
        Monetary fixBaseSideAmount = model.getTakersBaseSideAmount();
        Monetary fixQuoteSideAmount = model.getTakersQuoteSideAmount();
        String formattedBaseAmount = AmountFormatter.formatAmount(fixBaseSideAmount, false);
        String formattedQuoteAmount = AmountFormatter.formatAmount(fixQuoteSideAmount);
        Direction direction = model.getBisqEasyOffer().getTakersDirection();
        if (direction.isSell()) {
            toSendAmountDescription = Res.get("bisqEasy.tradeWizard.review.toSend");
            toReceiveAmountDescription = Res.get("bisqEasy.tradeWizard.review.toReceive");
            toSendAmount = formattedBaseAmount;
            toSendCode = fixBaseSideAmount.getCode();
            toReceiveAmount = formattedQuoteAmount;
            toReceiveCode = fixQuoteSideAmount.getCode();
        } else {
            toSendAmountDescription = Res.get("bisqEasy.tradeWizard.review.toPay");
            toReceiveAmountDescription = Res.get("bisqEasy.tradeWizard.review.toReceive");
            toSendAmount = formattedQuoteAmount;
            toSendCode = fixQuoteSideAmount.getCode();
            toReceiveAmount = formattedBaseAmount;
            toReceiveCode = fixBaseSideAmount.getCode();
        }

        reviewDataDisplay.setDirection(Res.get(direction.isSell() ? "offer.sell" : "offer.buy").toUpperCase() + " Bitcoin");
        reviewDataDisplay.setToSendAmountDescription(toSendAmountDescription.toUpperCase());
        reviewDataDisplay.setToSendAmount(toSendAmount);
        reviewDataDisplay.setToSendCode(toSendCode);
        reviewDataDisplay.setToReceiveAmountDescription(toReceiveAmountDescription.toUpperCase());
        reviewDataDisplay.setToReceiveAmount(toReceiveAmount);
        reviewDataDisplay.setToReceiveCode(toReceiveCode);
        reviewDataDisplay.setPaymentMethodDescription(Res.get("bisqEasy.tradeWizard.review.paymentMethodDescription").toUpperCase());
        reviewDataDisplay.setPaymentMethod(model.getPaymentMethod());
    }

    @Override
    public void onDeactivate() {
    }

    void onShowOpenTrades() {
        closeAndNavigateToHandler.accept(NavigationTarget.BISQ_EASY_OPEN_TRADES);
    }

    private void applyPriceDetails(PriceSpec priceSpec, Market market) {
        Optional<PriceQuote> marketPriceQuote = marketPriceService.findMarketPrice(market)
                .map(MarketPrice::getPriceQuote);
        String marketPrice = marketPriceQuote
                .map(PriceFormatter::formatWithCode)
                .orElse(Res.get("data.na"));
        Optional<Double> percentFromMarketPrice;
        percentFromMarketPrice = PriceUtil.findPercentFromMarketPrice(marketPriceService, priceSpec, market);
        double percent = percentFromMarketPrice.orElse(0d);
        if ((priceSpec instanceof FloatPriceSpec || priceSpec instanceof MarketPriceSpec)
                && percent == 0) {
            model.setPriceDetails(Res.get("bisqEasy.tradeWizard.review.priceDetails.seller", marketPrice));
        } else {
            String aboveOrBelow = percent > 0 ?
                    Res.get("offer.price.above") :
                    Res.get("offer.price.below");
            String percentAsString = percentFromMarketPrice.map(Math::abs).map(PercentageFormatter::formatToPercentWithSymbol)
                    .orElse(Res.get("data.na"));
            if (priceSpec instanceof FloatPriceSpec) {
                model.setPriceDetails(Res.get("bisqEasy.tradeWizard.review.priceDetails.seller.float",
                        percentAsString, aboveOrBelow, marketPrice));
            } else {
                if (percent == 0) {
                    model.setPriceDetails(Res.get("bisqEasy.tradeWizard.review.priceDetails.seller.fix.atMarket", marketPrice));
                } else {
                    model.setPriceDetails(Res.get("bisqEasy.tradeWizard.review.priceDetails.seller.fix",
                            percentAsString, aboveOrBelow, marketPrice));
                }
            }
        }
    }

    private void applyPriceQuote(Optional<PriceQuote> priceQuote) {
        String formattedPrice = priceQuote
                .map(PriceFormatter::format)
                .orElse("");
        String codes = priceQuote.map(e -> e.getMarket().getMarketCodes()).orElse("");
        model.setPrice(Res.get("bisqEasy.tradeWizard.review.price", formattedPrice, codes));
    }
}
