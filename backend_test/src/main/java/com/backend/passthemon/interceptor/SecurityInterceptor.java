package com.backend.passthemon.interceptor;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.backend.passthemon.annotation.AccessLimit;
import com.backend.passthemon.annotation.RequestAuthority;
import com.backend.passthemon.annotation.RequestConsistent;
import com.backend.passthemon.repository.UserRepository;
import com.backend.passthemon.redis.RedisService;
import com.backend.passthemon.utils.authorityutils.AuthorityUtil;
import com.backend.passthemon.utils.iputils.IpAddressUtil;
import com.backend.passthemon.utils.msgutils.Msg;
import com.backend.passthemon.utils.msgutils.MsgUtil;
import com.backend.passthemon.utils.tokenutils.JwtUtil;
import com.backend.passthemon.wrapper.RequestWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Slf4j
public class SecurityInterceptor implements HandlerInterceptor {

    @Autowired
    UserRepository userRepository;

    @Autowired
    private RedisService redisService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception{
        String jwt = getJwt(request);
        Integer userid= JwtUtil.getUserid(jwt);
        List<String> userAuthority=userRepository.getAuthorityByUserid(userid);
        Msg msg;
        if(!isAuthority(handler,userAuthority,userid,request)){
            msg = MsgUtil.makeMsg(MsgUtil.PERMISSION_DENIED,MsgUtil.PERMISSION_DENIED_MSG);
            sendJsonBack(response,msg);
            return false;
        }
        else{
            if(!isLimiting(request,handler)){
                msg = MsgUtil.makeMsg(MsgUtil.VISITING_TOO_OFTEN,MsgUtil.VISITING_TOO_OFTEN_MSG);
                sendJsonBack(response,msg);
                return false;
            }
            return true;
        }
    }

    //?????????????????????????????????,???????????????????????????api,?????????????????????????????????,????????????????????????userid??????????????????????????????
    private boolean isAuthority(Object handler, List<String> userAuthority,Integer userid,HttpServletRequest request) {
        boolean flag = true;
        if (handler instanceof HandlerMethod) {
            //?????????????????????
            Method method = ((HandlerMethod) handler).getMethod();
            //??????????????????????????????????????????????????????????????????true,??????
            RequestAuthority annotation = method.getAnnotation(RequestAuthority.class);
            RequestConsistent annotation2 = method.getAnnotation(RequestConsistent.class);
            if(annotation2 != null){
                //?????????????????????????????????
                boolean shouldConsistent =annotation2.identityConsistent();
                //???????????????
                if(shouldConsistent){
                    //??????????????????
                    String MethodName=request.getMethod();
                    if(MethodName.equals("GET")){
                        String tmp=request.getParameter("goodsId");
                        String stringUserid=request.getParameter("userId");
                        Integer requestUserid = Integer.parseInt(request.getParameter("userId"));
                        if(!requestUserid.equals(userid)) return false;
                    }else if(MethodName.equals("POST")){
                        try{
                            String requestBody = new RequestWrapper(request).getBodyString();
                            JSONObject jsonObject=JSONObject.parseObject(requestBody);
                            Integer requestUserid=jsonObject.getInteger("userId");
                            if(!requestUserid.equals(userid)) return false;

                        }catch (Exception e){
                            e.printStackTrace();
                        }
                    }
                }
            }
            if (annotation != null) {
                //??????????????????????????????
                String[] values = annotation.value();
                //???????????????????????????????????????
                boolean andAuthority = annotation.andAuthority();
                if (andAuthority) {//??????????????????????????????????????????
//                    flag = containsCheck(Arrays.asList(values), authorlist);
                    flag = AuthorityUtil.containCheck(Arrays.asList(values),userAuthority);
                } else {
                    flag = AuthorityUtil.haveCheck(Arrays.asList(values),userAuthority);
                }
            }
        }
        return flag;
    }
    //??????????????????????????????????????????
    private boolean isLimiting(HttpServletRequest request,Object handler){
        Msg msg;
        if (handler instanceof HandlerMethod) {
            HandlerMethod handlerMethod = (HandlerMethod) handler;
            Method method = handlerMethod.getMethod();
            if (!method.isAnnotationPresent(AccessLimit.class)) {
                return true;
            }
            AccessLimit accessLimit = method.getAnnotation(AccessLimit.class);
            if (accessLimit == null) {
                return true;
            }
            int limit = accessLimit.limit();
            Long sec= (long) (accessLimit.seconds());
            String key = IpAddressUtil.getIpAddress(request) + request.getRequestURI();
            Integer maxLimit = (Integer) redisService.get(key);
            if (maxLimit == null) {
                //set???????????????????????????
                redisService.set(key, 1, sec, TimeUnit.SECONDS);
            } else if (maxLimit < limit) {
                redisService.set(key, maxLimit + 1, sec, TimeUnit.SECONDS);
            } else {
                return false;
            }
        }
        return true;
    }
    private String getJwt(HttpServletRequest request) {
        return request.getHeader("Token");
    }

    private void sendJsonBack(HttpServletResponse response, Msg msg){
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json; charset=utf-8");
        try (PrintWriter writer = response.getWriter()) {
            String jsonBack = JSON.toJSONString(msg);
            log.info("[Security Interceptor] : " + jsonBack);
            writer.print(jsonBack);
        } catch (IOException e) {
            System.out.println("[Security Interceptor] : send-json-back error.");
        }
    }
}
