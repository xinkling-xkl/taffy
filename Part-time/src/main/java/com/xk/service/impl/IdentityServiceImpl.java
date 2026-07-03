package com.xk.service.impl;

import com.xk.dto.IdentityDTO;
import com.xk.entity.Identity;
import com.xk.mapper.IdentityMapper;
import com.xk.service.IdentityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
public class IdentityServiceImpl implements IdentityService {
    @Autowired
    private IdentityMapper identityMapper;

    @Override
    public void addIdentity(IdentityDTO identity) {
        Identity identityEntity = toIdentityEntity(identity);
        identityEntity.setData(new Date());
        identityEntity.setStatus("待审核");
        identityMapper.insert(identityEntity);
    }

    @Override
    public boolean deleteIdentity(int id) {
        return identityMapper.deleteById(id) > 0;
    }

    @Override
    public boolean updateIdentity(Identity identity) {
        return identityMapper.update(identity) > 0;
    }

    @Override
    public Identity getIdentityById(int id) {
        return identityMapper.selectById(id);
    }

    @Override
    public List<Identity> getIdentityByUserId(int userId) {
        return identityMapper.selectByUserId(userId);
    }

    @Override
    public List<Identity> getAllIdentity() {
        return identityMapper.selectAll();
    }

    private Identity toIdentityEntity(IdentityDTO dto) {
        if (dto == null) return null;
        Identity identity = new Identity();
        if (dto.getId() != null) identity.setId(dto.getId());
        identity.setUserId(dto.getUserId() != null ? dto.getUserId() : 0);
        identity.setImageurl(dto.getImageurl());
        identity.setBname(dto.getBname());
        identity.setRespond(dto.getRespond());
        identity.setStatus(dto.getStatus());
        identity.setData(dto.getData());
        return identity;
    }
}
