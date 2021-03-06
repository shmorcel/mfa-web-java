package controllers;

import models.LocalUser;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.Security;

public class Secured extends Security.Authenticator {

    @Override
    public String getUsername(Http.Context ctx) {
        String email = ctx.session().get("email");
        if (email == "" || email == null) {
            return null;
        }

        LocalUser user = LocalUser.findByEmail(email);
        if (user == null || (user.mfa_email != null && user.mfa_email != "" && (user.mfa_authenticated == null || !user.mfa_authenticated))) {
            return null;
        }

        return email;
    }

    @Override
    public Result onUnauthorized(Http.Context ctx) {
        return redirect(routes.Application.index());
    }
}
