package com.chh.campustix.module.ticket;

import com.chh.campustix.common.api.Result;
import com.chh.campustix.module.ticket.dto.TicketVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    @GetMapping("/mine")
    public Result<List<TicketVO>> mine() {
        return Result.ok(ticketService.mine());
    }
}
