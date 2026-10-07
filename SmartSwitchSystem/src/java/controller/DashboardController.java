package controller;
import java.io.IOException;
import java.time.*;
import java.util.*;
import javax.servlet.ServletException;
import javax.servlet.http.*;
import ui.*;
import static ui.UiSupport.*;
/** Server-rendered replacement for the dashboard's former JavaScript preview. */
public class DashboardController extends HttpServlet {
    private static final String STATE="NO_JS_UI_STATE",FLASH="NO_JS_UI_FLASH";
    private static final Map<String,String> PAGES=new LinkedHashMap<>();
    static {
        PAGES.put("USER_MANAGER","UserManager");
        PAGES.put("PERMISSION_MANAGER","PermissionManager");
        PAGES.put("ACCOUNT_MANAGER","AccountManager");
        PAGES.put("DEVICE_CONTROL","DeviceControl");
        PAGES.put("DEVICE_MANAGER","DeviceManager");
        PAGES.put("SCHEDULE_MANAGER","ScheduleManager");
        PAGES.put("CONTROL_HISTORY","ControlHistory");
    }
    @Override protected void doGet(HttpServletRequest r,HttpServletResponse response)throws ServletException,IOException {
        process(r,response);
    }
    @Override protected void doPost(HttpServletRequest r,HttpServletResponse response)throws ServletException,IOException {
        process(r,response);
    }
    private void redirect(HttpServletRequest r,HttpServletResponse response,String page,String suffix) {
        response.setStatus(HttpServletResponse.SC_SEE_OTHER);
        response.setHeader("Location",response.encodeRedirectURL(r.getContextPath()+"/MainController?action="+page+suffix));
    }
    private void process(HttpServletRequest r,HttpServletResponse response)throws ServletException,IOException {
        r.setCharacterEncoding("UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control","no-store");
        String page=r.getAttribute("UI_PAGE")==null?param(r,"action"):str(r.getAttribute("UI_PAGE"));
        if(!PAGES.containsKey(page)) {
            response.sendError(400,"Unknown UI page");
            return;
        }
        HttpSession session=r.getSession();
        PreviewState state;
        synchronized(session) {
            state=(PreviewState)session.getAttribute(STATE);
            if(state==null) {
                state=new PreviewState();
                session.setAttribute(STATE,state);
            }
            Object flash=session.getAttribute(FLASH);
            if(flash!=null) {
                r.setAttribute("uiMessage",flash);
                session.removeAttribute(FLASH);
            }
        }
        synchronized(state) {
            String op=param(r,"uiOp");
            try {
                if("downloadTrace".equals(op)&&"CONTROL_HISTORY".equals(page)&&"GET".equals(r.getMethod())) {
                    String id=String.valueOf(number(param(r,"id"),1,Integer.MAX_VALUE,"Mã nhật ký"));
                    String body=json(PreviewViews.trace(state,id));
                    response.setContentType("application/json;charset=UTF-8");
                    response.setHeader("Content-Disposition","attachment; filename=log-"+id+".json");
                    response.getWriter().write(body);
                    return;
                }
                if("POST".equals(r.getMethod())&&!op.isEmpty()) {
                    if(!state.csrf.equals(r.getParameter("csrf"))) {
                        response.setStatus(403);
                        throw new IllegalArgumentException("Phiên biểu mẫu không hợp lệ. Tải lại trang rồi thử lại.");
                    }
                    if("resetPreview".equals(op)) {
                        session.removeAttribute(STATE);
                        session.setAttribute(FLASH,"Đã đặt lại dữ liệu minh họa của session.");
                        redirect(r,response,page,"");
                        return;
                    }
                    if("SCHEDULE_MANAGER".equals(page)&&Arrays.asList("repeatDaily","repeatWeekdays","repeatWeekend","repeatOnce","shift15","shift60").contains(op)) {
                        Map<String,Object> draft=PreviewActions.scheduleDraft(r);
                        if(op.startsWith("shift")) {
                            LocalTime before=time(str(draft.get("time")));
                            int minutes="shift15".equals(op)?15:60;
                            LocalTime after=before.plusMinutes(minutes);
                            if("once".equals(draft.get("mode"))&&after.isBefore(before))draft.put("date",LocalDate.parse(str(draft.get("date"))).plusDays(1).toString());
                            draft.put("time",after.toString());
                        }
                        else {
                            draft.put("mode","repeatOnce".equals(op)?"once":"weekly");
                            if(!"repeatOnce".equals(op))draft.put("days",new ArrayList<Object>("repeatDaily".equals(op)?Arrays.asList(1,2,3,4,5,6,0):"repeatWeekend".equals(op)?Arrays.asList(6,
                            0):Arrays.asList(1,2,3,4,5)));
                        }
                        r.setAttribute("uiDraft",draft);
                    }
                    else {
                        String suffix=PreviewActions.handle(page,state,r);
                        session.setAttribute(FLASH,r.getAttribute("uiMessage"));
                        redirect(r,response,page,suffix);
                        return;
                    }
                }
                else if(!op.isEmpty()) {
                    response.setStatus(405);
                    throw new IllegalArgumentException("Thao tác thay đổi dữ liệu chỉ chấp nhận POST.");
                }
            }
            catch(IllegalArgumentException|java.time.format.DateTimeParseException e) {
                r.setAttribute("uiError",e.getMessage());
                r.setAttribute("uiFormName",param(r,"formName"));
                if("SCHEDULE_MANAGER".equals(page)&&"POST".equals(r.getMethod()))try {
                    r.setAttribute("uiDraft",PreviewActions.scheduleDraft(r));
                }
                catch(IllegalArgumentException ignored) {
                }
            }
            Map<String,Object> view;
            try {
                view=PreviewViews.prepare(page,state,r);
            }
            catch(IllegalArgumentException e) {
                r.setAttribute("uiError",e.getMessage());
                r.setAttribute("uiHideDialog",true);
                r.removeAttribute("uiFormName");
                if("SCHEDULE_MANAGER".equals(page))r.setAttribute("uiDraft",row("id","","name","","switchId","esp01-g16","time","06:30","action","ON",
                "mode","weekly","date",LocalDate.now(ZONE).toString(),"days",new ArrayList<Object>(Arrays.asList(1,2,3,4,5))));
                view=PreviewViews.prepare(page,state,new SafeViewRequest(r));
            }
            r.setAttribute("ui",view);
            r.setAttribute("activePage",page);
        }
        r.getRequestDispatcher("/web/"+PAGES.get(page)+".jsp").forward(r,response);
    }
    /** Invalid record/filter URLs recover to the page without reflecting raw input. */
    private static final class SafeViewRequest extends HttpServletRequestWrapper {
        SafeViewRequest(HttpServletRequest r) {
            super(r);
        }
        @Override public String getParameter(String name) {
            return "action".equals(name)?super.getParameter(name):null;
        }
    }
}
