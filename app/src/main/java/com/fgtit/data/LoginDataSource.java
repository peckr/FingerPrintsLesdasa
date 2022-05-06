package com.fgtit.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.util.Log;

import com.fgtit.data.model.LoggedInUser;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOError;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Iterator;
import java.util.regex.Pattern;

import  com.fgtit.data.Configuraciones;

/**
 * Class that handles authentication w/ login credentials and retrieves user information.
 */
public class LoginDataSource {
    String perfil;
    LoggedInUser fakeUser;
    UserLoginTask userLoginTask;

    public Result<LoggedInUser> login(String username, String password) {
         userLoginTask = new UserLoginTask(username,password);
        try {

           return userLoginTask.execute().get();

        } catch (Exception e) {
            return new Result.Error(new IOException("Error logging in", e));
        }
    }

    public void logout() {
        // TODO: revoke authentication
    }

    public class UserLoginTask extends AsyncTask<Void, Void, Result<LoggedInUser> > {

        private final String mEmail;
        private final String mPassword;
        private String dataecriptada="";

        URL url = null;
        BufferedReader bufferedReader = null;
        BufferedWriter bufferedWriter = null;
        InputStreamReader streamReader = null;
        InputStream inputStream;
      //  AesCipher seguridad;
        String keypro ="0123456789abcdef";
        String iv ="abcdef9876543210";
      //  AesCipher encrypted;
      //  AesCipher decrypted ;


        UserLoginTask(String email, String password) {
            mEmail = email;
            mPassword = password;

        }

        @Override
        protected Result<LoggedInUser> doInBackground(Void... params) {
            // TODO: attempt authentication against a network service.

            JSONObject parametros = new JSONObject();
            String data = mEmail.trim()+"|"+mPassword.trim();
            AesCipher seguridad = null;
            String keypro ="0123456789abcdef";
            String iv ="abcdef9876543210";
            AesCipher encrypted = null;
            AesCipher decrypted ;
            // Log.d("data",data);

            try {

                encrypted=  seguridad.encrypt(keypro,data);
                dataecriptada = encrypted.getData();
            } catch (Exception e) {
                e.printStackTrace();
            }
                Log.d("dataencriptada",dataecriptada);
           // decrypted = seguridad.decrypt(keypro, encrypted.getData());
            // Log.d("datadesencriptada",decrypted.getData());
            //  Log.d("initvector",encrypted.getInitVector());

            try {
                url = new URL(Configuraciones.urlServer+"loginapp?params="+dataecriptada+"|"+encrypted.initVector);
            } catch (MalformedURLException e) {
                e.printStackTrace();
                return new Result.Error(new IOException("Error en la Url", e));
            }
            Log.d("coxion)","entra a conectar");
            HttpURLConnection urlConnection = null;
            try {
                urlConnection = (HttpURLConnection) url.openConnection();
            } catch (IOException e) {
                e.printStackTrace();
                return new Result.Error(new IOException("Oops problemas de red", e));
            }
            try {
                urlConnection.setRequestMethod("GET");
            } catch (ProtocolException e) {

                e.printStackTrace();
                return new Result.Error(new IOException("Error en el método ", e));
            }

            urlConnection.setRequestProperty("Content-Type","application/x-www-form-urlencoded");

            //  Log.d("Connect)","ok");


            try {
                inputStream = urlConnection.getInputStream();
                Log.d("inputStream)","ok");
            } catch (IOException e) {
                e.printStackTrace();
                Log.d("inputStream)",e.toString());
                return new Result.Error(new IOException("Oops problemas de red en Stream", e));
            }



            try {
                streamReader = new InputStreamReader(inputStream,"UTF-8");
                Log.d("InputStreamReader","OK");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
                Log.d("InputStreamReader)",e.toString());
            }

            bufferedReader = new BufferedReader(streamReader);

            StringBuffer buffer = new StringBuffer();
            String line = null;
            try {
                line = bufferedReader.readLine();
            } catch (IOException e) {
                e.printStackTrace();
            }

            while (line != null){

                buffer.append(line);
                break;
            }

            try {
                bufferedReader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            perfil= buffer.toString().replaceAll("\"","");
              Log.d("perfil",perfil.toString());

            // TODO: register the new account here.
           String []paramsx = perfil.toString().split(Pattern.quote("|"));

               if (paramsx[0].trim().toString().equals("S") ){

                   fakeUser =
                           new LoggedInUser(
                                   java.util.UUID.randomUUID().toString(),
                                   paramsx[3]);
                   Log.d("Usuario",fakeUser.getDisplayName());
                   return new Result.Success<>(fakeUser);
               }else{
                   Log.d("Usuario","entro a else "+paramsx[0]+"-"+paramsx[1]);
                   switch (Integer.parseInt(paramsx[1])){

                       case 101:   IOException e = new IOException("Usuario o contraseña incorrecta");
                           return new Result.Error(e);

                       case 103:    IOException a = new IOException("Oops no tienes permisos");
                           return new Result.Error(a);

                       default:   IOException b = new IOException("Error en credenciales");
                           return new Result.Error(b);

                   }

               }



        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }


        @Override
        protected void onCancelled() {

        }



        public String getPostDataString(JSONObject params) throws Exception {

            StringBuilder result = new StringBuilder();
            boolean first = true;

            Iterator<String> itr = params.keys();

            while(itr.hasNext()){

                String key= itr.next();
                Object value = params.get(key);

                if (first)
                    first = false;
                else
                    result.append("&");

                result.append(URLEncoder.encode(key, "UTF-8"));
                result.append("=");
                result.append(URLEncoder.encode(value.toString(), "UTF-8"));

            }

            Log.d("from",result.toString());
            return result.toString();
        }

        public Boolean getResultjson(JSONObject params) throws Exception {

            StringBuilder result = new StringBuilder();
            boolean first;
            first = params.getBoolean("status");
            return  first;
        }

        public String getResultjsonError(JSONObject params) throws Exception {

            StringBuilder result = new StringBuilder();
            String first;
            first = params.getString("data");
            return  first;
        }
    }


}