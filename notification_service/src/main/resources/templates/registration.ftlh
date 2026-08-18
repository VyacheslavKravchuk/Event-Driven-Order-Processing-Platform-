<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <style>
        .btn { background: #3498db; color: white; padding: 10px 20px; text-decoration: none; border-radius: 5px; }
    </style>
</head>
<body>
    <div style="font-family: sans-serif; padding: 20px;">
        <h2>Добро пожаловать, ${firstName}!</h2>
        <p>Ваш аккаунт <b>${username}</b> успешно активирован.</p>
        <p>Теперь вы можете войти в систему и начать работу:</p>
        <br>
        <a href="https://inovexx.com" class="btn">Войти в личный кабинет</a>
        <br><br>
        <hr>
        <p style="font-size: 12px; color: grey;">
            © ${year?c} Inovexx. Если возникли вопросы, пишите на ${supportEmail}
        </p>
    </div>
</body>
</html>