package jit.wxs.demo.service.impl;

import com.alipay.api.internal.util.AlipaySignature;
import com.baomidou.mybatisplus.mapper.EntityWrapper;
import com.baomidou.mybatisplus.service.impl.ServiceImpl;
import jit.wxs.demo.entity.OrderInfo;
import jit.wxs.demo.mapper.OrderInfoMapper;
import jit.wxs.demo.service.OrderInfoService;
import jit.wxs.demo.utils.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author jitwxs
 * @since 2018-06-04
 */
@Service
public class OrderInfoServiceImpl extends ServiceImpl<OrderInfoMapper, OrderInfo> implements OrderInfoService {
    @Value("${alipay.app_id}")
    private String APP_ID;
    @Value("${alipay.alipay_public_key}")
    private String ALIPAY_PUBLIC_KEY;
    @Value("${alipay.sign_type}")
    private String SIGN_TYPE;

    @Autowired
    private OrderInfoMapper orderInfoMapper;

    /**
     * 生成订单
     * @param subject 订单名称
     * @param body 订单描述
     * @param money 支付金额
     * @param sellerId 收款商户ID
     * @author jitwxs
     * @since 2018/6/4 16:29
     */
    @Override
    public OrderInfo createOrder(String subject, String body, float money, String sellerId) {
        // 生成商户订单号
        String orderId = RandomUtils.time();

        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setOrderId(orderId);
        orderInfo.setSubject(subject);
        orderInfo.setBody(body);
        orderInfo.setMoney(money);
        orderInfo.setSellerId(sellerId);
        orderInfo.setCreateDate(new Date());
        /*
         * 订单状态（与官方统一）
         * WAIT_BUYER_PAY：交易创建，等待买家付款；
         * TRADE_CLOSED：未付款交易超时关闭，或支付完成后全额退款；
         * TRADE_SUCCESS：交易支付成功；
         * TRADE_FINISHED：交易结束，不可退款
         */
        orderInfo.setStatus("WAIT_BUYER_PAY");

        orderInfoMapper.insert(orderInfo);

        return orderInfo;
    }

    /**
     * 校验订单
     * 支付宝同步/异步回调时调用
     * @author jitwxs
     * @since 2018/6/4 16:40
     */
    @Override
    public boolean validOrder(Map<String,String> params) throws Exception {
        /* 实际验证过程建议商户务必添加以下校验：
        1、需要验证该通知数据中的out_trade_no是否为商户系统中创建的订单号，
        2、判断total_amount是否确实为该订单的实际金额（即商户订单创建时的金额），
        3、校验通知中的seller_id（或者seller_email) 是否为out_trade_no这笔单据的对应的操作方（有的时候，一个商户可能有多个seller_id/seller_email）
        4、验证app_id是否为该商户本身。
        */

        // 1、调用SDK验证签名
        boolean signVerified = AlipaySignature.rsaCheckV1(params, ALIPAY_PUBLIC_KEY, "utf-8", SIGN_TYPE);
        if(!signVerified) {
            return false;
        }
        // 获取订单数据
        String orderId = params.get("out_trade_no");
        OrderInfo orderInfo = orderInfoMapper.selectById(orderId);
        if(orderInfo == null) {
            return false;
        }
        // 2、判断金额是否相等
        float money = Float.parseFloat(params.get("total_amount"));
        if(money != orderInfo.getMoney()) {
            return false;
        }

        // 3、判断商户ID是否相等
        String sellerId = params.get("seller_id");
        if(!sellerId.equals(orderInfo.getSellerId())) {
            return false;
        }
        // 4、判断APP_ID是否相等
        String appId = params.get("app_id");
        if(!appId.equals(APP_ID)) {
            return false;
        }

        return true;
    }

    @Override
    public OrderInfo getByIdOrAlipayNo(String orderId, String alipayNo) {
        if(StringUtils.isNotBlank(orderId)) {
            OrderInfo orderInfo = orderInfoMapper.selectById(orderId);
            if(orderInfo != null) {
                return orderInfo;
            }
        }

        if(StringUtils.isNotBlank(alipayNo)) {
            List<OrderInfo> list = orderInfoMapper.selectList(new EntityWrapper<OrderInfo>().eq("alipay_no", alipayNo));
            if(list != null && list.size() > 0) {
                return list.get(0);
            }
        }

        return null;
    }

    /*
     * 订单状态（与官方统一）
     * WAIT_BUYER_PAY：交易创建，等待买家付款；
     * TRADE_CLOSED：未付款交易超时关闭，或支付完成后全额退款；
     * TRADE_SUCCESS：交易支付成功；
     * TRADE_FINISHED：交易结束，不可退款
     */

    @Override
    public boolean changeStatus(String orderId, String status, String... tradeNo) {
        // 判断参数是否合法
        OrderInfo orderInfo = orderInfoMapper.selectById(orderId);
        if(orderInfo == null) {
            return false;
        }
        List<String> list = Arrays.asList("WAIT_BUYER_PAY", "TRADE_CLOSED", "TRADE_SUCCESS", "TRADE_FINISHED");
        if(StringUtils.isBlank(status) || !list.contains(status)) {
            return false;
        }

        // 更新状态
        if("TRADE_SUCCESS".equals(status) && tradeNo.length > 0) {
            orderInfo.setAlipayNo(tradeNo[0]);
        }
        orderInfo.setStatus(status);
        orderInfoMapper.updateById(orderInfo);
        return true;
    }
}