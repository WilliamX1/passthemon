package com.backend.passthemon.controller;

import com.alibaba.fastjson.JSONObject;
import com.backend.passthemon.annotation.AccessLimit;
import com.backend.passthemon.annotation.RequestConsistent;
import com.backend.passthemon.annotation.RequestLimit;
import com.backend.passthemon.dto.UserInfoDto;
import com.backend.passthemon.entity.Follow;
import com.backend.passthemon.entity.User;
import com.backend.passthemon.entity.Verification;
import com.backend.passthemon.service.FollowService;
import com.backend.passthemon.service.VerificationService;
import com.backend.passthemon.utils.emailutils.EmailUtil;
import com.backend.passthemon.service.EmailService;
import com.backend.passthemon.service.UserService;
import com.backend.passthemon.utils.msgutils.Msg;
import com.backend.passthemon.utils.msgutils.MsgUtil;
import com.backend.passthemon.utils.userutils.UserInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@Slf4j
@RestController
@CrossOrigin
public class UserController {
    @Autowired
    private UserService userService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private VerificationService verificationService;

    @Autowired
    private FollowService followService;

    @RequestMapping("/user/login")
    public Msg login(@RequestParam(name = "email") String email,
                     @RequestParam(name = "password") String password){
        log.info("[UserController] : /user/login");
        return userService.login(email, password);
    }

    @RequestMapping("/user/sendEmail")
    public Msg sendEmail(@RequestParam(name = "email") String email,
                         @RequestParam(name = "opt") String opt){
        try{
            log.info("[UserController] : /user/sendEmail");
            if(!EmailUtil.isSJTUEmail(email)) {
                log.info("email format error");
                return MsgUtil.makeMsg(MsgUtil.ERROR, MsgUtil.ERROR_MSG);
            }
            if (opt.isEmpty()) {
                return MsgUtil.makeMsg(MsgUtil.ERROR, MsgUtil.ERROR_MSG);
            }
            else if (opt.equals("signup")) {
                User user = userService.getUserByEmail(email);
                if(user!=null) {
                    return MsgUtil.makeMsg(MsgUtil.ACCOUNT_ALREADY_EXIST, MsgUtil.ACCOUNT_ALREADY_EXIST_MSG);
                }
            }
            else if (!opt.equals("reset")) {
                return MsgUtil.makeMsg(MsgUtil.ERROR, MsgUtil.ERROR_MSG);
            }

            Verification verification = verificationService.generateAndSave(email);
            String verificationCode = verification.getCode();
            emailService.sendSimpleEmail(EmailUtil.FROM, email, EmailUtil.SUBJECT, EmailUtil.makeText(verificationCode));
            return MsgUtil.makeMsg(MsgUtil.ALL_OK, MsgUtil.ALL_OK_MSG);
        }
        catch(Exception e){
            e.printStackTrace();
            log.info("caught exception");
            return MsgUtil.makeMsg(MsgUtil.ERROR, MsgUtil.ERROR_MSG);
        }
    }

    @RequestMapping("/user/signup")
    public Msg signup(@RequestParam(name = "email") String email,
                      @RequestParam(name = "password") String password,
                      @RequestParam(name = "code") String code) {
        log.info("[UserController] : /user/signup");
        // account already exists
        if(userService.getUserByEmail(email)!=null)
            return MsgUtil.makeMsg(MsgUtil.ACCOUNT_ALREADY_EXIST, MsgUtil.ACCOUNT_ALREADY_EXIST_MSG);
        Verification verification = verificationService.getVerificationByEmail(email);
        if(verification==null || verification.getCode()==null
                || !verification.getCode().equals(code))
            return MsgUtil.makeMsg(MsgUtil.ERROR, MsgUtil.ERROR_MSG);
        // expired
        if(verification.getTime() < System.currentTimeMillis())
            return MsgUtil.makeMsg(MsgUtil.CODE_EXPIRED, MsgUtil.CODE_EXPIRED_MSG);
        User user = userService.signup(email, password);
        return user==null ? MsgUtil.makeMsg(MsgUtil.ERROR, MsgUtil.ERROR_MSG) : MsgUtil.makeMsg(MsgUtil.ALL_OK, MsgUtil.ALL_OK_MSG);
    }

    @RequestMapping("/user/reset")
    public Msg reset(@RequestParam(name = "email") String email,
                     @RequestParam(name = "password") String password,
                     @RequestParam(name = "code") String code) {
        log.info("[UserController] : /user/reset");
        if(userService.getUserByEmail(email)==null)
            return MsgUtil.makeMsg(MsgUtil.ACCOUNT_NOT_EXIST, MsgUtil.ACCOUNT_NOT_EXIST_MSG);
        Verification verification = verificationService.getVerificationByEmail(email);
        if(verification==null || verification.getCode()==null
                || !verification.getCode().equals(code))
            return MsgUtil.makeMsg(MsgUtil.ERROR, MsgUtil.ERROR_MSG);
        // expired
        if(verification.getTime() < System.currentTimeMillis())
            return MsgUtil.makeMsg(MsgUtil.CODE_EXPIRED, MsgUtil.CODE_EXPIRED_MSG);
        User user = userService.reset(email, password);
        return user==null ? MsgUtil.makeMsg(MsgUtil.ERROR, MsgUtil.ERROR_MSG) : MsgUtil.makeMsg(MsgUtil.ALL_OK, MsgUtil.ALL_OK_MSG);
    }

    @RequestMapping("/user/getUserByUserId")
    public Msg getUserByUserId(Integer userId){
        User result = userService.getUserByUserId(userId);
        JSONObject jsonObject = JSONObject.parseObject(JSONObject.toJSONString(result));
        return MsgUtil.makeMsg(MsgUtil.ALL_OK, MsgUtil.ALL_OK_MSG, jsonObject);
    }

    @RequestMapping("/user/getUserInfoByUserId")
    @RequestConsistent(identityConsistent = true)
    public Msg getUserInfoByUserId(Integer userId){
        UserInfo userInfo = userService.getUserInfoBuUserId(userId);
        JSONObject jsonObject=JSONObject.parseObject(JSONObject.toJSONString(userInfo));
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        return MsgUtil.makeMsg(MsgUtil.ALL_OK, MsgUtil.ALL_OK_MSG, jsonObject, request);
    }

    @RequestMapping("/user/getOtherInfo")
    public Msg getOtherInfo( @RequestParam("otherId") Integer userid, @RequestParam("userId") Integer myId){
        //先获得其他人的信息
        UserInfo userInfo = userService.getUserInfoBuUserId(userid);
        //获得自己的关注列表
        List<Follow> follows = userService.getFollowsByUserid(myId);
        //遍历follows，看是否已经关注
        for (Follow follow : follows) {
            if (follow.getFollower().equals(userid)) {
                userInfo.setFollowId(follow.getId());
                break;
            }
        }
        JSONObject jsonObject=JSONObject.parseObject(JSONObject.toJSONString(userInfo));
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        return MsgUtil.makeMsg(MsgUtil.ALL_OK, MsgUtil.ALL_OK_MSG, jsonObject, request);
    }

    @RequestMapping("/user/getUserNameById")
    public Msg getUserNameById(@RequestParam("userId") Integer userid){
        String result=userService.getUserNameById(userid);
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("username", result);
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        return MsgUtil.makeMsg(MsgUtil.ALL_OK, MsgUtil.ALL_OK_MSG, jsonObject, request);
    }
    @RequestConsistent(identityConsistent = true)
    @RequestMapping("/user/submitChange")
    public Msg editUser(@RequestBody JSONObject param){
        Integer userid=param.getInteger("userId");
        String username=param.getString("name");
        String phone=param.getString("phone");
        String image=param.getString("image");
        Integer gender=param.getInteger("gender");
        userService.editUser(userid,username,phone,image,gender);
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("flag",true);
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        return MsgUtil.makeMsg(MsgUtil.ALL_OK, MsgUtil.ALL_OK_MSG,jsonObject, request);
    }

    @RequestMapping("/user/followOther")
    @RequestConsistent(identityConsistent = true)
    public Msg followOther(@RequestParam("otherId") Integer userid, @RequestParam("userId") Integer myId){
        User user=userService.getUserByUserId(myId);
        Follow follow=new Follow(userid,user);
        followService.addFollow(follow);
        //返回followId
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("followId",follow.getId());
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        return MsgUtil.makeMsg(MsgUtil.ALL_OK, MsgUtil.ALL_OK_MSG,jsonObject, request);
    }
    @RequestMapping("/user/unFollowOther")
    @RequestConsistent(identityConsistent = true)
    public Msg unFollowOther(@RequestParam("followId") Integer followId){
        followService.unFollow(followId);
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("followId",-1);
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        return MsgUtil.makeMsg(MsgUtil.ALL_OK, MsgUtil.ALL_OK_MSG,jsonObject, request);
    }
    @RequestMapping("/user/getUsersByPage")
    @RequestConsistent(identityConsistent = true)
    public Msg getUsersByPage(@RequestParam("userId") Integer userid, @RequestParam("fetchPage") Integer fetchPage){
        List<UserInfoDto> result=userService.listFollowingsByPage(fetchPage,userid);
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("list", result);
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        return MsgUtil.makeMsg(MsgUtil.ALL_OK, MsgUtil.ALL_OK_MSG, jsonObject, request);
    }
}
