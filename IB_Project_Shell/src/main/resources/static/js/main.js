function download() {

	var xhr = new XMLHttpRequest();
	xhr.open('GET', "/api/demo/download", true);
	xhr.responseType = 'blob';

	xhr.onload = function(e) {
		if (this.status == 200) {
			var blob = this.response;
			console.log(blob);
			var a = document.createElement('a');
			var url = window.URL.createObjectURL(blob);
			a.href = url;
			a.download = xhr.getResponseHeader('filename');
			a.click();
			window.URL.revokeObjectURL(url);
		}
	};

	xhr.send();
};

function registerForm(){
	$('#loginForm').hide();
	$('#registerForm').show();

}

function loginForm(){
	$('#loginForm').show();
	$('#registerForm').hide();

}

function register(){
	var user = {
			'email' : $('#emailRegister').val(),
			'password': $('#passwordRegister').val(),
			'firstname' : $('#firstnameRegister').val(),
			'lastname': $('#lastnameRegister').val()
		}
	
	if(user.email=="" || user.password=="" || user.firstname=="" || user.lastname==""){
		$('#emptyFieldError').show();
		$('#wrongEmailError').hide();
		return;
	}
		var userJSON = JSON.stringify(user);
		$.ajax({
		    url : '/auth/register',
		    type: 'POST',
		    data : userJSON,
		    contentType:"application/json; charset=utf-8",
		    dataType:"json",
		    success: function(data)
		    {
		    	$('#wrongEmailError').hide();
		    	$('#registerForm').hide();
		    	alert("You are now registered.Enter your email and password and login!")
		    	$('#loginForm').show();
		    },
		    error: function (error)
		    {
		    	$('#wrongEmailError').show();
		    	return;
		    }
		});
		    
}