package com.backend.passthemon.repositoryimpl;

import com.backend.passthemon.dao.DemandDao;
import com.backend.passthemon.dto.DemandInfoDto;
import com.backend.passthemon.entity.Demand;
import com.backend.passthemon.entity.User;
import com.backend.passthemon.repository.DemandRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

import static com.backend.passthemon.constant.GoodsConstant.*;
import static com.backend.passthemon.constant.GoodsConstant.BEING_AUDITED;

@Repository
public class DemandRepositoryImpl implements DemandRepository {
    @Autowired
    DemandDao demandDao;

    @Override
    public Integer addDemand(Demand demand){
        Demand result=demandDao.saveAndFlush(demand);
        return result.getId();
    }

    @Override
    public List<Demand> listAllDemandsByPage(Integer fetchPage, Integer category, Integer attrition){
        Pageable pageable = PageRequest.of(fetchPage, 8, Sort.Direction.DESC, "upLoadTime");
        Page<Demand> demandPage;
        if(category == 0 && attrition == 0){
            demandPage = demandDao.findAllByState(1, pageable);;
        }
        else{
            if(category != 0 && attrition == 0){
                demandPage = demandDao.findAllByStateAndCategory(1, category, pageable);
            }
            else{
                if(category == 0){
                    demandPage = demandDao.findAllByStateAndAttrition( 1, attrition, pageable);
                }
                else{
                    demandPage= demandDao.findAllByStateAndAttritionAndCategory(1, attrition, category, pageable);
                }
            }
        }
        List<Demand> result = demandPage.getContent();

        return result;
    }

    @Override
    public List<Demand> listOnesDemandsByPage(Integer fetchPage, User user){
        Pageable pageable = PageRequest.of(fetchPage, 8, Sort.Direction.DESC, "upLoadTime");
        List<Integer> stateList = new ArrayList<>();
        stateList.add(IMAGE_FAILED_AUDIT);
        stateList.add(TEXT_FAILED_AUDIT);
        stateList.add(ON_SALE);
        stateList.add(BEING_AUDITED);
        Page<Demand> demandPage = demandDao.findAllByUserAndStateIn(pageable, user, stateList);
        List<Demand> result = demandPage.getContent();

        return result;
    }
    @Override
    public Demand getDemandById(Integer demandId){
        return demandDao.findDemandById(demandId);
    }
    @Override
    public void editDemand(DemandInfoDto demandInfoDto){
        Demand demand=demandDao.findDemandById(demandInfoDto.getDemandId());
        demand.setName(demandInfoDto.getName());
        demand.setNum(demandInfoDto.getNum());
        demand.setDescription(demandInfoDto.getDescription());
        demand.setIdealPrice(demandInfoDto.getIdealPrice());
        demand.setCategory(demandInfoDto.getCategory());
        demand.setAttrition(demandInfoDto.getAttrition());
        demandDao.saveAndFlush(demand);
    }
    @Override
    public  void removeDemand(Integer demandId){
        Demand demand=demandDao.findDemandById(demandId);
        demand.setState(0);
        demandDao.saveAndFlush(demand);
    }
    @Override
    public  void changeState(Integer demandId,Integer state){
        Demand demand=demandDao.findDemandById(demandId);
        demand.setState(state);
        demandDao.saveAndFlush(demand);
    }
}
