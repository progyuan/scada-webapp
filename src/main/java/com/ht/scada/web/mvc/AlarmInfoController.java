package com.ht.scada.web.mvc;

import com.alibaba.fastjson.JSON;
import com.ht.scada.common.tag.entity.EndTag;
import com.ht.scada.common.tag.entity.EndTagExtInfo;
import com.ht.scada.common.tag.service.EndTagService;
import com.ht.scada.security.entity.User;
import com.ht.scada.security.service.UserService;
import com.ht.scada.web.alarm.AlarmCache;
import com.ht.scada.web.entity.AlarmHandle;
import com.ht.scada.web.entity.AlarmRecord;
import com.ht.scada.web.entity.UserExtInfo;
import com.ht.scada.web.service.AlarmInfoService;
import com.ht.scada.web.service.UserExtInfoService;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.shiro.crypto.hash.Sha256Hash;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 报警信息Controller
 * @author Administrator
 */
@Controller
@RequestMapping("/alarm")
public class AlarmInfoController {

    private static final Logger log = LoggerFactory.getLogger(AlarmInfoController.class);
    private static final String STR_CONFIRM = "confirm";
    private static final String STR_HANDLE = "handle";
    @Autowired
    private AlarmInfoService alarmInfoService;
    @Autowired
    private UserService userService;
    @Autowired
    private UserExtInfoService userExtInfoService;
    @Autowired
    private EndTagService endTagService;

    /**
     * 历史报警信息
     * @return 
     */
    @RequestMapping(value = "history")
    @ResponseBody
    public List<AlarmRecord> history() {
        List<AlarmRecord> list = alarmInfoService.getHistoryAlarmRecord(1);
        List<AlarmRecord> alarmList = new ArrayList<>();
        User user = userService.getCurrentUser();
        UserExtInfo userExtInfo = userExtInfoService.findUserExtInfoByUserID(user.getId());
        Set<Integer> endTagIDs = userExtInfo.getEndTagID();
        for (AlarmRecord rec : list) {
            EndTag endTag = rec.getEndTag();
            if (endTagIDs.contains(endTag.getId())) {
                alarmList.add(rec);
            }
        }
        return alarmList;
    }

    /**
     * 实时报警
     * @return 
     */
    @RequestMapping(value = "realtime")
    @ResponseBody
    public Map<String, List> realtime() {
        List<AlarmRecord> list = alarmInfoService.getHistoryAlarmRecord(0);
        List<AlarmRecord> alarmList = new ArrayList<>();
        Map<String, List> map = new HashMap<>();
        User user = userService.getCurrentUser();
        UserExtInfo userExtInfo = userExtInfoService.findUserExtInfoByUserID(user.getId());
        Set<Integer> endTagIDs = userExtInfo.getEndTagID();
        for (AlarmRecord rec : list) {
            EndTag endTag = rec.getEndTag();
            if (endTagIDs.contains(endTag.getId())) {
                //按井分类,增加到列表
                List<AlarmRecord> tmplist;
                if (map.containsKey(endTag.getCode())) {
                    tmplist = map.get(endTag.getCode());
                } else {
                    tmplist = new ArrayList<>();
                    map.put(endTag.getCode(), tmplist);
                }
                tmplist.add(rec);
            }
        }

        return map;
    }

    /**
     * 获得报警信息
     * @param id
     * @return 
     */
    @RequestMapping(value = "getAlarmById")
    @ResponseBody
    public AlarmRecord getAlarmById(String id) {
//        return alarmInfoService.getAlarmByID(id);
        return AlarmCache.getInstance().getAlarm(id);
    }

    /**
     * 报警回复处理
     * @param alarmId
     * @param userName
     * @param type
     * @param msg
     * @return 
     */
    @RequestMapping(value = "confirm")
    @ResponseBody
    public boolean confirm(String alarmId, String userName, String type,String msg) {
        String[] ids = alarmId.split(",");
        for (String id : ids) {
            AlarmRecord alarm = alarmInfoService.getAlarmByAlarmId(id);
            Date curDate = new Date();
            AlarmHandle record = null;
            for (AlarmHandle rec : alarm.getAlarmHandleList()) {
                if (rec.getUser().getUsername().equals(userName)) {
                    record = rec;
                }
            }
            if (record == null) {
                record = new AlarmHandle();
                User usr = userService.getUserByUsername(userName);
                record.setUser(usr);
                record.setAlarmRecord(alarm);
                //alarm.getAlarmHandleList().add(record);
            }
            switch (type) {
                case STR_CONFIRM:
                    //报警回复
                    record.setConfirmTime(curDate);
                    alarmInfoService.saveAlarmHandle(record);
                    break;
                case STR_HANDLE:
                    //报警处理
                    record.setHandleTime(curDate);
                    record.setHandleMsg(msg);
                    alarmInfoService.saveAlarmHandle(record);
                    break;
            }
        }
        return true;
    }

    /**
     * 获得系统时间
     * @return 
     */
    @RequestMapping(value = "now")
    @ResponseBody
    public Date now() {
        return new Date();
    }

    /**
     * 获得井信息
     * @param code
     * @return 
     */
    @RequestMapping(value = "endTagExtInfo")
    @ResponseBody
    public Map<String, String> endTagExtInfo(String code) {
        Map<String, String> map = new HashMap<>();
        EndTag endTag = endTagService.getByCode(code);
        List<EndTagExtInfo> extList = endTag.getExtInfo();
        for (EndTagExtInfo ext : extList) {
            map.put(ext.getKeyName().toLowerCase(), ext.getValue());
        }
        return map;
    }
    
    /**
     * 获得报警信息（android客户端用）
     * @param username
     * @return 
     */
    @RequestMapping(value = "mobile")
    @ResponseBody
    public List<AlarmRecord> mobile(String username) {
        List<AlarmRecord> alarmList = new ArrayList<>();
        UserExtInfo userExtInfo = userExtInfoService.findUserExtInfoByUserName(username);
        if (!userExtInfo.getHeadflg().equals("1")) {
            return alarmList;
        }
        List<AlarmRecord> list = alarmInfoService.getHistoryAlarmRecord(0);
        Set<Integer> endTagIDs = userExtInfo.getEndTagID();

        for (AlarmRecord record : list) {
            if(false){
                log.debug(JSON.toJSONString(record));
                return null;
            }
            //判断如果井不属于用户管理,则跳过
            if (!endTagIDs.contains(record.getEndTag().getId())) {
                continue;
            }
            alarmList.add(record);
        }
        log.debug(JSON.toJSONString(alarmList));
        return alarmList;
    }

    /**
     * 移动客户端登录
     * @param username
     * @param password
     * @return 
     */
    @RequestMapping(value = "mobileLogin")
    @ResponseBody
    public Map<String, String> mobileLogin(String username, String password) {
        //new Sha256Hash(oldpass).toHex())
        Map<String, String> map = new HashMap<>();
        User user = userService.getUserByUsername(username);
        if (user == null) {
            map.put("state", "0");
            return map;
        }
        if (user.getPassword().equals(new Sha256Hash(password).toHex())) {
            map.put("state", "1");
            map.put("username", user.getUsername());
            map.put("realname", user.getName());
        } else {
            map.put("state", "0");
        }
        return map;
    }
}
