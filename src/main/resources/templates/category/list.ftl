<html>
<#include "../common/header.ftl">

<body>
<div id="wrapper" class="toggled">

    <#--边栏sidebar-->
    <#include "../common/nav.ftl">

    <#--主要内容content-->
    <div id="page-content-wrapper">
        <div class="container-fluid">
            <div class="row clearfix">
                <div class="col-md-12 column">
                    <table class="table table-bordered table-condensed">
                        <thead>
                        <tr>
                            <th>账号id</th>
                            <th>账号</th>
                            <th>昵称</th>
                            <th>删除</th>
                        </tr>
                        </thead>
                        <tbody>

                        <#list users as user>
                        <tr>
                            <td>${user.id}</td>
                            <td>${user.username}</td>
                            <td>${user.nickname}</td>
                            <td><a href="/u/deleteUser?userId=${user.id}">删除</a></td>
                        </tr>
                        </#list>
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
    </div>

</div>
</body>
</html>