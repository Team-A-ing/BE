package com.readb.service.analysis;

import com.readb.common.exception.BusinessException;
import com.readb.common.exception.ErrorCode;
import com.readb.domain.promise.Promise;
import com.readb.repository.PromiseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PromiseService {

    private final PromiseRepository promiseRepository;

    @Transactional(readOnly = true)
    public List<Promise> getPromisesByLeader(Long leaderId) {
        return promiseRepository.findByOwnerIdOrderByCreatedAtDesc(leaderId);
    }
}
