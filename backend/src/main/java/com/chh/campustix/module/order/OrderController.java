package com.chh.campustix.module.order;

import com.chh.campustix.common.api.Result;
import com.chh.campustix.module.order.dto.OrderCreateReq;
import com.chh.campustix.module.order.dto.OrderCreateVO;
import com.chh.campustix.module.order.dto.OrderPayVO;
import com.chh.campustix.module.order.dto.OrderVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /** 下单前领取幂等 token */
    @GetMapping("/token")
    public Result<String> token() {
        return Result.ok(UUID.randomUUID().toString().replace("-", ""));
    }

    @PostMapping
    public Result<OrderCreateVO> create(@Valid @RequestBody OrderCreateReq req) {
        return Result.ok(orderService.create(req));
    }

    @GetMapping
    public Result<List<OrderVO>> mine(@RequestParam(defaultValue = "1") long pageNo,
                                      @RequestParam(defaultValue = "10") long pageSize) {
        return Result.ok(orderService.listMine(pageNo, pageSize));
    }

    @GetMapping("/{orderNo}")
    public Result<OrderVO> detail(@PathVariable String orderNo) {
        return Result.ok(orderService.detail(orderNo));
    }

    @PostMapping("/{orderNo}/pay")
    public Result<OrderPayVO> pay(@PathVariable String orderNo, HttpServletRequest request) {
        String base = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();
        return Result.ok(orderService.pay(orderNo,
                base + "/api/payments/alipay/notify",
                base + "/orders"));
    }

    @PostMapping("/{orderNo}/cancel")
    public Result<Boolean> cancel(@PathVariable String orderNo) {
        orderService.cancel(orderNo);
        return Result.ok(true);
    }
}
