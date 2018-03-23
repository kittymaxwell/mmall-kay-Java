package com.kay.service.impl;

import com.google.common.collect.Lists;
import com.kay.common.Const;
import com.kay.common.ResponseCode;
import com.kay.common.ServerResponse;
import com.kay.dao.CartMapper;
import com.kay.dao.ProductMapper;
import com.kay.pojo.Cart;
import com.kay.pojo.Product;
import com.kay.service.ICartService;
import com.kay.util.BigDecimalUtil;
import com.kay.util.PropertiesUtil;
import com.kay.vo.CartProductVo;
import com.kay.vo.CartVo;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Created by kay on 2018/3/23.
 */
@Service("iCartService")
public class CartServiceImpl implements ICartService {


    @Autowired
    private CartMapper cartMapper;

    @Autowired
    private ProductMapper productMapper;

    /**
     * 添加到购物车
     * @param userId
     * @param productId
     * @return
     */
    public ServerResponse<CartVo> add(Integer userId, Integer productId,Integer count) {
        if (productId == null || count == null) {
            return ServerResponse.createByErrorCodeMessage(ResponseCode.ILLEGAL_ARGUMENT.getCode(), ResponseCode.ILLEGAL_ARGUMENT.getDescription());
        }

        //1.判断购物车是否有该商品
        Cart cart = cartMapper.selectByUserIdAndProductId(userId, productId);
        if (cart == null) {
            Cart insertCart = new Cart();
            insertCart.setUserId(userId);
            insertCart.setProductId(productId);
            insertCart.setQuantity(count);
            //默认勾选
            insertCart.setChecked(Const.Cart.CHECKED);
            cartMapper.insert(insertCart);
        }else {
            //已有产品，数量相加
            count = count + cart.getQuantity();
            cart.setQuantity(count);
            cartMapper.updateByPrimaryKeySelective(cart);
        }

        //2.返回VO 对象，此VO对象会多次复用，建立一个复用方法，即根据用户生成购物车VO对象
        CartVo cartVo = this.getCartVoLimit(userId);
        return ServerResponse.createBySuccess(cartVo);
    }

    /**
     * 购物车返回对象生成
     * @param userId
     * @return
     */
    private CartVo getCartVoLimit(Integer userId) {
        CartVo cartVo = new CartVo();
        List<CartProductVo> cartProductVoList = Lists.newArrayList();
        BigDecimal totalPrice = new BigDecimal("0.0");

        //取出该用户所有订单，组装 CartProductVo
        List<Cart> cartList = cartMapper.selectByUserId(userId);
        if (CollectionUtils.isNotEmpty(cartList)) {
            for (Cart cart : cartList) {
                CartProductVo cartProductVo = new CartProductVo();
                cartProductVo.setId(cart.getId());
                cartProductVo.setUserId(userId);
                cartProductVo.setProductId(cart.getProductId());
                cartProductVo.setProductChecked(cart.getChecked());

                Product product = productMapper.selectByPrimaryKey(cart.getProductId());
                if (product != null) {
                    cartProductVo.setProductMainImage(product.getMainImage());
                    cartProductVo.setProductName(product.getName());
                    cartProductVo.setProductSubtitle(product.getSubtitle());
                    cartProductVo.setProductStatus(product.getStatus());
                    cartProductVo.setProductPrice(product.getPrice());
                    cartProductVo.setProductStock(product.getStock());//库存

                    int buyLimitCount = 0;//最终的数量
                    //判断库存是否足够
                    if (product.getStock() >= cart.getQuantity()) {
                        buyLimitCount = cart.getQuantity();
                        cartProductVo.setLimitQuantity(Const.Cart.LIMIT_NUM_SUCCESS);
                    }else {
                        buyLimitCount = product.getStock();
                        cartProductVo.setLimitQuantity(Const.Cart.LIMIT_NUM_FAIL);
                        //检测到库存不足时，修改购物车中数量
                        Cart updateStockCart = new Cart();
                        updateStockCart.setId(cart.getId());
                        updateStockCart.setQuantity(buyLimitCount);
                        cartMapper.updateByPrimaryKeySelective(updateStockCart);
                    }
                    cartProductVo.setQuantity(buyLimitCount);
                    //计算该商品总价，利用封装好的BigDecimalUtil工具类
                    cartProductVo.setProductTotalPrice(BigDecimalUtil.mul(product.getPrice().doubleValue(), cartProductVo.getQuantity()));
                }
                //计算选中商品的购物车总价
                if (cartProductVo.getProductChecked() == Const.Cart.CHECKED) {
                    totalPrice = BigDecimalUtil.add(totalPrice.doubleValue(), cartProductVo.getProductTotalPrice().doubleValue());
                }
                cartProductVoList.add(cartProductVo);
            }
        }
        cartVo.setCartTotalPrice(totalPrice);
        cartVo.setCartProductVoList(cartProductVoList);
        //是否全选
        cartVo.setAllChecked(this.getAllCheckedStatus(userId));
        cartVo.setImageHost(PropertiesUtil.getProperty("ftp.server.http.prefix"));
        return cartVo;
    }

    /**
     * 是否全选
     * @param userId
     * @return
     */
    private boolean getAllCheckedStatus(Integer userId) {
        if (userId == null) {
            return false;
        }
        return cartMapper.selectUnCheckedStatusCountByUserId(userId) == 0;
    }


}
