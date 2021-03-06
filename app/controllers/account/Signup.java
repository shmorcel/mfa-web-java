package controllers.account;

import com.fasterxml.jackson.databind.JsonNode;
import controllers.Application;
import models.LocalUser;
import models.utils.AppException;
import models.utils.Hash;
import play.Configuration;
import play.Logger;
import play.Play;
import play.data.Form;
import play.i18n.Messages;
import play.libs.F;
import play.libs.ws.WS;
import play.libs.ws.WSResponse;
import play.mvc.Controller;
import play.mvc.Result;
import views.html.account.signup.confirm;
import views.html.account.signup.create;
import views.html.account.signup.created;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.UUID;

import static play.data.Form.form;

/**
 * Signup to PlayStartApp : save.
 * <p/>
 * User: yesnault
 * Date: 31/01/12
 */
public class Signup extends Controller {

    /**
     * Display the create form.
     *
     * @return create form
     */
    public static Result create() {
        return ok(create.render(form(Application.Register.class)));
    }

    /**
     * Display the create form only (for the index page).
     *
     * @return create form
     */
    public static Result createFormOnly() {
        return ok(create.render(form(Application.Register.class)));
    }

    /**
     * Save the new user.
     *
     * @return Successfull page or created form if bad
     */
    public static F.Promise<Result> save() {
        Form<Application.Register> registerForm = form(Application.Register.class).bindFromRequest();

        if (registerForm.hasErrors()) {
            return F.Promise.pure(badRequest(create.render(registerForm)));
        }

        Application.Register register = registerForm.get();
        Result resultError = checkBeforeSave(registerForm, register.email);

        if (resultError != null) {
            return F.Promise.pure(resultError);
        }

        try {
            LocalUser user = new LocalUser();
            user.email = register.email;
            user.fullname = register.fullname;
            user.passwordHash = Hash.createPassword(register.inputPassword);
            user.confirmationToken = UUID.randomUUID().toString();

            // Temporary confirm user
            user.validated = true;

            final String mfaSite = Play.application().configuration().getString("mfa.site");

            F.Promise<WSResponse> responsePromise = WS.url(mfaSite + "/api/v9/is_user_valid")
                    .setQueryParameter("email", register.email)
                    .setQueryParameter("uid", Application.appUID)
                    .setQueryParameter("secret", Application.appSecret)
                    .setContentType("application/x-www-form-urlencoded")
                    .post("");

            F.Promise<Result> resultPromise = responsePromise.map(new F.Function<WSResponse, Result>() {
                @Override
                public Result apply(WSResponse wsResponse) throws Throwable {
                    JsonNode json = wsResponse.asJson();

                    if (json.has("valid") && json.get("valid").asBoolean()) {
                        if (json.has("registration_state") && json.get("registration_state").asText().equals("finished")) {
                            // Using the same email as Acceptto one.
                            user.mfa_email = register.email;
                            Logger.debug("MFA email has set to " + register.email);
                        } else {
                            Logger.warn("User has started registration in Acceptto but hasn't finished");
                        }
                    }

                    user.save();

                    return ok(created.render());
                }
            });

            return resultPromise;
        } catch (Exception e) {
            Logger.error("Signup.save error", e);
            flash("error", Messages.get("error.technical"));
        }
        return F.Promise.pure(badRequest(create.render(registerForm)));
    }

    /**
     * Check if the email already exists.
     *
     * @param registerForm User Form submitted
     * @param email email address
     * @return Index if there was a problem, null otherwise
     */
    private static Result checkBeforeSave(Form<Application.Register> registerForm, String email) {
        // Check unique email
        if (LocalUser.findByEmail(email) != null) {
            flash("error", Messages.get("error.email.already.exist"));
            return badRequest(create.render(registerForm));
        }

        return null;
    }

    /**
     * Valid an account with the url in the confirm mail.
     *
     * @param token a token attached to the user we're confirming.
     * @return Confirmationpage
     */
    public static Result confirm(String token) {
        LocalUser user = LocalUser.findByConfirmationToken(token);
        if (user == null) {
            flash("error", Messages.get("error.unknown.email"));
            return badRequest(confirm.render());
        }

        if (user.validated) {
            flash("error", Messages.get("error.account.already.validated"));
            return badRequest(confirm.render());
        }

        try {
            if (LocalUser.confirm(user)) {
                flash("success", Messages.get("account.successfully.validated"));
                return ok(confirm.render());
            } else {
                Logger.debug("Signup.confirm cannot confirm user");
                flash("error", Messages.get("error.confirm"));
                return badRequest(confirm.render());
            }
        } catch (AppException e) {
            Logger.error("Cannot signup", e);
            flash("error", Messages.get("error.technical"));
        }
        return badRequest(confirm.render());
    }
}
