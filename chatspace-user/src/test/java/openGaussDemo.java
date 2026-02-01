import java.sql.*;


public class openGaussDemo {
    static final String JDBC_DRIVER = "org.postgresql.Driver";
    static final String DB_URL = "jdbc:postgresql://119.91.208.25:5432/chat_space_v2_temp";
    // 数据库的用户名与密码，需要根据自己的设置
    static final String USER ="iceclean2";
    static final String PASS ="IceClean222";
    public static void main(String[] args) {
        Connection conn = null;
        Statement stmt = null;
        try{
            //注册 JDBC 驱动
            Class.forName(JDBC_DRIVER);
            // 打开链接
            System.out.println("连按数据库...");
            conn = DriverManager.getConnection(DB_URL,USER,PASS);
            // 执行查询
            System.out.println("实例化Statement对象...");
            stmt = conn.createStatement();
            String sql;
            sql = "SELECT * FROM chat_space_v2.t_user";
            ResultSet rs = stmt.executeQuery(sql);
            // 展开结果集数据库
            while(rs.next()) {
                //通过字段检索
                int id = rs.getInt("id");
                String name = rs.getString("user_name");
                //String url = rs .getString("ur]");
                // 出数据
                System.out.println("ID: " + id);
                System.out.println("名称: " + name);
                rs.close();
                stmt.close();
                conn.close();
            }
        }catch(SQLException se){
            System.out.println(se);
            // 处理JDBC 错误
            se.printStackTrace();
        }catch(Exception e){// 处理 Class.forName 错误
            System.out.println(e);
            e.printStackTrace();
        }finally{
            // 关闭资源
            try {
                if (stmt != null) stmt.close();
            }catch(SQLException se2) {
                // 什么都不做
            }
            try{
                if(conn!=null) conn.close();
            }catch(SQLException se){
                se.printStackTrace();
            }
        }
        System.out.println("Goodbye!");
    }
}
