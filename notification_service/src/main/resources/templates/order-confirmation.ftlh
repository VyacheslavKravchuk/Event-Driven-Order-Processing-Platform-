<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
</head>
<body style="font-family: Arial, sans-serif; color: #333; line-height: 1.6; padding: 20px;">

    <div style="max-width: 600px; margin: 0 auto; border: 1px solid #eee; padding: 20px; border-radius: 10px;">

        <h2 style="color: #2c3e50;">
            <#if status == "CANCELLED">
                Заказ #${orderId} отменен
            <#elseif status == "PAID">
                Оплата заказа #${orderId} получена!
            <#elseif status == "SHIPPED">
                Ваш заказ #${orderId} уже в пути!
            <#elseif status == "COMPLETED">
                Заказ #${orderId} успешно доставлен
            <#else>
                Информация по заказу #${orderId}
            </#if>
        </h2>

        <p>Здравствуйте, <b>${customerName!'Клиент'}</b>!</p>

        <p>Текущий статус вашего заказа:
            <span style="padding: 3px 8px; background: #f2f2f2; border-radius: 4px; font-weight: bold;">
                ${status}
            </span>
        </p>

        <h3 style="border-bottom: 2px solid #f2f2f2; padding-bottom: 10px;">Детали заказа:</h3>
        <table style="width: 100%; border-collapse: collapse; margin-bottom: 20px;">
            <thead>
                <tr style="background-color: #f8f9fa;">
                    <th style="border: 1px solid #ddd; padding: 10px; text-align: left;">Товар</th>
                    <th style="border: 1px solid #ddd; padding: 10px; text-align: center;">Кол-во</th>
                    <th style="border: 1px solid #ddd; padding: 10px; text-align: right;">Цена</th>
                </tr>
            </thead>
            <tbody>
                <#list items as item>
                <tr>
                    <td style="border: 1px solid #ddd; padding: 10px;">${item.productName!'Без названия'}</td>
                    <td style="border: 1px solid #ddd; padding: 10px; text-align: center;">${item.quantity!0}</td>
                    <td style="border: 1px solid #ddd; padding: 10px; text-align: right;">${item.price!0} ₽</td>
                </tr>
                </#list>
            </tbody>
        </table>

        <div style="text-align: right; font-size: 18px; font-weight: bold; color: #2c3e50;">
            Итого к оплате: ${totalAmount!0} ₽
        </div>

        <div style="margin-top: 30px; padding-top: 15px; border-top: 1px solid #eee; font-size: 12px; color: #7f8c8d;">
            <p>С уважением, команда <b>Inovexx</b></p>
            <p>Если у вас возникли вопросы, напишите нам: <a href="mailto:${supportEmail!'support@inovexx.com'}">${supportEmail!'support@inovexx.com'}</a></p>
        </div>
    </div>

</body>
</html>
