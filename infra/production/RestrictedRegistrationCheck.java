import com.campushub.CampusHubApplication;
import com.campushub.auth.CampusPrincipal;
import com.campushub.auth.IdentityMapper;
import com.campushub.registration.RegistrationService;
import com.campushub.activity.ActivityService;
import com.campushub.error.BusinessException;
import org.springframework.boot.builder.SpringApplicationBuilder;

/** Separate, trusted service-layer acceptance process. Never an HTTP/login endpoint. */
class RestrictedRegistrationCheck {
    public static void main(String[] args) {
        if(args.length!=2 || !args[0].equals("--apply")) throw new IllegalArgumentException("Explicit --apply and activity ID required");
        long id=Long.parseLong(args[1]);
        try(var context=new SpringApplicationBuilder(CampusHubApplication.class).run("--server.port=0","--spring.profiles.active=prod")) {
            var users=context.getBean(IdentityMapper.class);
            var account=users.findByLoginName("restricted_student");
            var actor=new CampusPrincipal(account,users.roles(account.id()));
            var service=context.getBean(RegistrationService.class);
            var before=service.mine(id,actor);
            var registered=service.change(id,before.version(),false,actor);
            if(!registered.status().equals("CONFIRMED"))throw new IllegalStateException("Registration failed");
            var cancelled=service.change(id,registered.version(),true,actor);
            if(!cancelled.status().equals("CANCELLED") || !cancelled.id().equals(registered.id()))throw new IllegalStateException("Cancellation failed");
            boolean denied=false;
            try{context.getBean(ActivityService.class).managed(id,actor);}catch(BusinessException error){denied=error.status()==403;}
            if(!denied)throw new IllegalStateException("Student management access was not denied");
            System.out.println("PASS: real MySQL registration/cancellation and service-role isolation; HTTP WeChat identity NOT tested");
        }
    }
}
