package org.knowm.xchange.yobit.service;

import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.marketdata.Trades;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.dto.trade.OpenOrders;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.dto.trade.UserTrades;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.NotAvailableFromExchangeException;
import org.knowm.xchange.exceptions.NotYetImplementedForExchangeException;
import org.knowm.xchange.service.trade.TradeService;
import org.knowm.xchange.service.trade.params.CancelOrderByIdParams;
import org.knowm.xchange.service.trade.params.CancelOrderParams;
import org.knowm.xchange.service.trade.params.TradeHistoryParamCurrencyPair;
import org.knowm.xchange.service.trade.params.TradeHistoryParamLimit;
import org.knowm.xchange.service.trade.params.TradeHistoryParamOffset;
import org.knowm.xchange.service.trade.params.TradeHistoryParams;
import org.knowm.xchange.service.trade.params.TradeHistoryParamsIdSpan;
import org.knowm.xchange.service.trade.params.TradeHistoryParamsSorted;
import org.knowm.xchange.service.trade.params.TradeHistoryParamsTimeSpan;
import org.knowm.xchange.service.trade.params.orders.OpenOrdersParamCurrencyPair;
import org.knowm.xchange.service.trade.params.orders.OpenOrdersParams;
import org.knowm.xchange.utils.DateUtils;
import org.knowm.xchange.yobit.YoBit;
import org.knowm.xchange.yobit.YoBitAdapters;
import org.knowm.xchange.yobit.YoBitExchange;
import org.knowm.xchange.yobit.dto.BaseYoBitResponse;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class YoBitTradeService extends YoBitBaseService<YoBit> implements TradeService {

  public YoBitTradeService(YoBitExchange exchange) {
    super(YoBit.class, exchange);
  }

  @Override
  public OpenOrders getOpenOrders() throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
    throw new NotYetImplementedForExchangeException("Need to specify OpenOrdersParams");
  }

  @Override
  public OpenOrders getOpenOrders(OpenOrdersParams params) throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
    if (params instanceof OpenOrdersParamCurrencyPair) {
      CurrencyPair currencyPair = ((OpenOrdersParamCurrencyPair) params).getCurrencyPair();
      String market = YoBitAdapters.adapt(currencyPair);
      BaseYoBitResponse response = service.activeOrders(
          exchange.getExchangeSpecification().getApiKey(),
          signatureCreator,
          "ActiveOrders",
          exchange.getNonceFactory(),
          market
      );

      if (!response.success)
        throw new ExchangeException("failed to get open orders");

      List<LimitOrder> orders = new ArrayList<>();

      if (response.returnData != null) {
        for (Object key : response.returnData.keySet()) {
          Map tradeData = (Map) response.returnData.get(key);

          String id = key.toString();

          LimitOrder order = YoBitAdapters.adaptOrder(id, tradeData);

          orders.add(order);
        }
      }

      return new OpenOrders(orders);
    }

    throw new IllegalStateException("Need to specify currency pair");
  }

  @Override
  public String placeMarketOrder(MarketOrder marketOrder) throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
    throw new NotAvailableFromExchangeException();
  }

  @Override
  public String placeLimitOrder(LimitOrder limitOrder) throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
    String market = YoBitAdapters.adapt(limitOrder.getCurrencyPair());
    String direction = limitOrder.getType().equals(Order.OrderType.BID) ? "buy" : "sell";
    BaseYoBitResponse response = service.trade(
        exchange.getExchangeSpecification().getApiKey(),
        signatureCreator,
        "Trade",
        exchange.getNonceFactory(),
        market,
        direction,
        limitOrder.getLimitPrice(),
        limitOrder.getTradableAmount()
    );

    if (!response.success)
      throw new ExchangeException("failed to get place order");

    return response.returnData.get("order_id").toString();
  }

  @Override
  public boolean cancelOrder(String orderId) throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
    return cancelOrder(new CancelOrderByIdParams(orderId));
  }

  @Override
  public boolean cancelOrder(CancelOrderParams orderParams) throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
    if (orderParams instanceof CancelOrderByIdParams) {
      CancelOrderByIdParams params = (CancelOrderByIdParams) orderParams;

      BaseYoBitResponse response = service.cancelOrder(
          exchange.getExchangeSpecification().getApiKey(),
          signatureCreator,
          "CancelOrder",
          exchange.getNonceFactory(),
          Long.valueOf(params.getOrderId())
      );

      return response.success;
    }

    throw new IllegalStateException("Need to specify order id");
  }

  @Override
  public UserTrades getTradeHistory(TradeHistoryParams params) throws IOException {
    Integer count = 1000;
    if (params instanceof TradeHistoryParamLimit) {
      count = ((TradeHistoryParamLimit) params).getLimit();
    }

    Long offset = 0L;
    if (params instanceof TradeHistoryParamOffset) {
      offset = ((TradeHistoryParamOffset) params).getOffset();
    }

    String market = null;
    if (params instanceof TradeHistoryParamCurrencyPair) {
      CurrencyPair currencyPair = ((TradeHistoryParamCurrencyPair) params).getCurrencyPair();
      market = YoBitAdapters.adapt(currencyPair);
    }

    Long fromTransactionId = null;
    Long endTransactionId = null;
    if (params instanceof TradeHistoryParamsIdSpan) {
      TradeHistoryParamsIdSpan tradeHistoryParamsIdSpan = (TradeHistoryParamsIdSpan) params;

      String startId = tradeHistoryParamsIdSpan.getStartId();
      if (startId != null)
        fromTransactionId = Long.valueOf(startId);

      String endId = tradeHistoryParamsIdSpan.getEndId();
      if (endId != null)
        endTransactionId = Long.valueOf(endId);
    }

    String order = "DESC";
    if (params instanceof TradeHistoryParamsSorted) {
      order = ((TradeHistoryParamsSorted) params).getOrder().equals(TradeHistoryParamsSorted.Order.desc) ? "DESC" : "ASC";
    }

    Long fromTimestamp = null;
    Long toTimestamp = null;
    if (params instanceof TradeHistoryParamsTimeSpan) {
      TradeHistoryParamsTimeSpan tradeHistoryParamsTimeSpan = (TradeHistoryParamsTimeSpan) params;

      Date startTime = tradeHistoryParamsTimeSpan.getStartTime();
      if (startTime != null)
        fromTimestamp = DateUtils.toUnixTimeNullSafe(startTime);

      Date endTime = tradeHistoryParamsTimeSpan.getEndTime();
      if (endTime != null)
        toTimestamp = DateUtils.toUnixTimeNullSafe(endTime);
    }

    BaseYoBitResponse response = service.tradeHistory(
        exchange.getExchangeSpecification().getApiKey(),
        signatureCreator,
        "TradeHistory",
        exchange.getNonceFactory(),
        offset,
        count,
        fromTransactionId,
        endTransactionId,
        order,
        fromTimestamp,
        toTimestamp,
        market
    );

    List<UserTrade> trades = new ArrayList<>();

    if (response.returnData != null) {
      for (Object key : response.returnData.keySet()) {
        Map tradeData = (Map) response.returnData.get(key);

        String id = key.toString();
        String type = tradeData.get("type").toString();
        String amount = tradeData.get("amount").toString();
        String rate = tradeData.get("rate").toString();
        String orderId = tradeData.get("order_id").toString();
        String pair = tradeData.get("pair").toString();
        String timestamp = tradeData.get("timestamp").toString();

        Date time = DateUtils.fromUnixTime(Long.valueOf(timestamp));

        UserTrade userTrade = new UserTrade(
            YoBitAdapters.adaptType(type),
            new BigDecimal(amount),
            YoBitAdapters.adaptCurrencyPair(pair),
            new BigDecimal(rate),
            time,
            id,
            orderId,
            null,
            null
        );

        trades.add(userTrade);
      }
    }

    return new UserTrades(trades, Trades.TradeSortType.SortByTimestamp);
  }

  @Override
  public TradeHistoryParams createTradeHistoryParams() {
    throw new NotYetImplementedForExchangeException();
  }

  @Override
  public OpenOrdersParams createOpenOrdersParams() {
    throw new NotYetImplementedForExchangeException();
  }

  @Override
  public void verifyOrder(LimitOrder limitOrder) {
    throw new NotYetImplementedForExchangeException();
  }

  @Override
  public void verifyOrder(MarketOrder marketOrder) {
    throw new NotYetImplementedForExchangeException();
  }

  @Override
  public Collection<Order> getOrder(String... orderIds) throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
    List<Order> orders = new ArrayList<>();

    for (String orderId : orderIds) {
      Long id = Long.valueOf(orderId);

      BaseYoBitResponse response = service.orderInfo(
          exchange.getExchangeSpecification().getApiKey(),
          signatureCreator,
          "OrderInfo",
          exchange.getNonceFactory(),
          id
      );

      if (response.returnData != null) {
        Map map = (Map) response.returnData.get(orderId);
        Order order = YoBitAdapters.adaptOrder(orderId, map);

        orders.add(order);
      }
    }

    return orders;
  }

}
