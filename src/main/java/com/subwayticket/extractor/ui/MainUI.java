package com.subwayticket.extractor.ui;

import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.subwayticket.database.model.TicketOrder;
import com.subwayticket.extractor.http.RESTfulAPIUtil;
import com.subwayticket.model.PublicResultCode;
import com.subwayticket.model.request.ExtractTicketRequest;
import com.subwayticket.model.request.LoginRequest;
import com.subwayticket.model.result.*;
import com.subwayticket.model.result.Result;
import sun.awt.OSInfo;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.ws.rs.core.Response;
import java.awt.*;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Created by zhou-shengyun on 7/31/16.
 */
public class MainUI {
    private JFrame window;
    private JTextField filePathTextField;
    private JButton browserButton;
    private JButton parseButton;
    private JPanel mainPanel;
    private JTable orderTable;
    private JPanel orderPanel;
    private JSpinner extractSpinner;
    private JButton extractButton;
    private JPasswordField systemAccountPasswordField;
    private JTextField systemAccountIdTextField;
    private JButton loginButton;

    private static final String[] ORDER_TABLE_COL_NAMES = {"订单号", "城市", "起始站", "终点站", "可取票数"};
    private TicketOrder ticketOrder;
    private String extractCode;
    private String token;

    public MainUI(JFrame parantWindow) {
        window = parantWindow;
        orderTable.setModel(new DefaultTableModel((Object[][]) null, ORDER_TABLE_COL_NAMES));
        orderTable.setRowHeight(30);
        browserButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                FileDialog fd = new FileDialog(window, "浏览文件", FileDialog.LOAD);
                fd.setVisible(true);
                if (fd.getFiles().length > 0) {
                    filePathTextField.setText(fd.getFiles()[0].getAbsolutePath());
                }
            }
        });
        parseButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                extractCode = null;
                try {
                    BufferedImage bi = ImageIO.read(new File(filePathTextField.getText()));
                    if (bi == null) {
                        JOptionPane.showMessageDialog(window, "图片格式不可识别。", "错误", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    LuminanceSource source = new BufferedImageLuminanceSource(bi);
                    Binarizer binarizer = new HybridBinarizer(source);
                    BinaryBitmap binaryBitmap = new BinaryBitmap(binarizer);
                    extractCode = new QRCodeReader().decode(binaryBitmap, null).getText();
                    getOrderInfo(true);
                } catch (IOException e1) {
                    JOptionPane.showMessageDialog(window, "读取图片文件失败。", "错误", JOptionPane.ERROR_MESSAGE);
                } catch (FormatException | ChecksumException | NotFoundException e2) {
                    e2.printStackTrace();
                    JOptionPane.showMessageDialog(window, "无法解析二维码。", "错误", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        loginButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        Response r = RESTfulAPIUtil.put(RESTfulAPIUtil.API_BASE_URL_V1 + "/system_account/login", new LoginRequest(
                                systemAccountIdTextField.getText(), systemAccountPasswordField.getText()), null);
                        MobileLoginResult result = (MobileLoginResult) RESTfulAPIUtil.parseResponse(r, MobileLoginResult.class);
                        token = result.getToken();
                        if (result.getResultCode() != PublicResultCode.SUCCESS)
                            JOptionPane.showMessageDialog(window, result.getResultDescription(), "错误", JOptionPane.ERROR_MESSAGE);
                        else
                            JOptionPane.showMessageDialog(window, result.getResultDescription(), "提示", JOptionPane.INFORMATION_MESSAGE);
                    }
                }).start();
            }
        });
        extractButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        Response r = RESTfulAPIUtil.put(RESTfulAPIUtil.API_BASE_URL_V1 + "/ticket_order/extract_ticket", new ExtractTicketRequest(
                                extractCode, Integer.valueOf(extractSpinner.getValue().toString())), token);
                        Result result = RESTfulAPIUtil.parseResponse(r);
                        if (result.getResultCode() != PublicResultCode.SUCCESS)
                            JOptionPane.showMessageDialog(window, result.getResultDescription(), "错误", JOptionPane.ERROR_MESSAGE);
                        else
                            JOptionPane.showMessageDialog(window, result.getResultDescription(), "提示", JOptionPane.INFORMATION_MESSAGE);
                        getOrderInfo(false);
                    }
                }).start();
            }
        });
    }

    private void getOrderInfo(final boolean showError) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Response r = RESTfulAPIUtil.get(RESTfulAPIUtil.API_BASE_URL_V1 + "/ticket_order/order_info/by_extractcode/" + extractCode, token);
                OrderInfoResult result = (OrderInfoResult) RESTfulAPIUtil.parseResponse(r, OrderInfoResult.class);
                if (result.getResultCode() != PublicResultCode.SUCCESS) {
                    if (showError)
                        JOptionPane.showMessageDialog(window, result.getResultDescription(), "错误", JOptionPane.ERROR_MESSAGE);
                    orderTable.setModel(new DefaultTableModel((Object[][]) null, ORDER_TABLE_COL_NAMES));
                    return;
                }
                Object[][] tableData = new Object[][]{{
                        result.getTicketOrder().getTicketOrderId(),
                        result.getTicketOrder().getStartStation().getSubwayLine().getCity().getCityName(),
                        result.getTicketOrder().getStartStation().getDisplayName(),
                        result.getTicketOrder().getEndStation().getDisplayName(),
                        result.getTicketOrder().getAmount() - result.getTicketOrder().getExtractAmount()
                }};
                orderTable.setModel(new DefaultTableModel(tableData, ORDER_TABLE_COL_NAMES));
                extractSpinner.setModel(new SpinnerNumberModel(
                        result.getTicketOrder().getAmount() - result.getTicketOrder().getExtractAmount(),
                        1, result.getTicketOrder().getAmount() - result.getTicketOrder().getExtractAmount(), 1
                ));
            }
        }).start();
    }

    public static void main(String[] args) {
        try {
            if (OSInfo.getOSType() == OSInfo.OSType.LINUX) {
                UIManager.setLookAndFeel("com.sun.java.swing.plaf.gtk.GTKLookAndFeel");
            } else
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        JFrame frame = new JFrame("地铁取票模拟器 - By AlohaWorld Team");
        frame.setLocationRelativeTo(null);
        frame.setContentPane(new MainUI(frame).mainPanel);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setSize(1024, 400);
        frame.setVisible(true);
    }

    {
// GUI initializer generated by IntelliJ IDEA GUI Designer
// >>> IMPORTANT!! <<<
// DO NOT EDIT OR ADD ANY CODE HERE!
        $$$setupUI$$$();
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        mainPanel = new JPanel();
        mainPanel.setLayout(new GridLayoutManager(4, 2, new Insets(0, 0, 0, 0), -1, -1));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(1, 4, new Insets(0, 15, 15, 15), -1, -1));
        mainPanel.add(panel1, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JLabel label1 = new JLabel();
        label1.setText("二维码图像文件:");
        panel1.add(label1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        filePathTextField = new JTextField();
        panel1.add(filePathTextField, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        browserButton = new JButton();
        browserButton.setText("浏览...");
        panel1.add(browserButton, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, new Dimension(150, -1), null, null, 0, false));
        parseButton = new JButton();
        parseButton.setText("开始解析");
        panel1.add(parseButton, new GridConstraints(0, 3, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, new Dimension(250, -1), null, null, 0, false));
        final Spacer spacer1 = new Spacer();
        mainPanel.add(spacer1, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(1, 1, new Insets(0, 15, 15, 15), -1, -1));
        mainPanel.add(panel2, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 1, false));
        orderPanel = new JPanel();
        orderPanel.setLayout(new GridLayoutManager(2, 1, new Insets(0, 10, 10, 10), -1, -1));
        panel2.add(orderPanel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        orderPanel.setBorder(BorderFactory.createTitledBorder("订单信息"));
        final Spacer spacer2 = new Spacer();
        orderPanel.add(spacer2, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        final JScrollPane scrollPane1 = new JScrollPane();
        orderPanel.add(scrollPane1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        orderTable = new JTable();
        orderTable.setAutoscrolls(true);
        orderTable.setEditingColumn(0);
        orderTable.setEditingRow(0);
        orderTable.setFillsViewportHeight(false);
        orderTable.setIntercellSpacing(new Dimension(1, 0));
        orderTable.setRowMargin(0);
        orderTable.setShowHorizontalLines(false);
        orderTable.setShowVerticalLines(false);
        orderTable.setSurrendersFocusOnKeystroke(false);
        scrollPane1.setViewportView(orderTable);
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new GridLayoutManager(1, 4, new Insets(0, 15, 15, 15), -1, -1));
        mainPanel.add(panel3, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final Spacer spacer3 = new Spacer();
        panel3.add(spacer3, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final JLabel label2 = new JLabel();
        label2.setText("提票数:");
        panel3.add(label2, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        extractSpinner = new JSpinner();
        panel3.add(extractSpinner, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        extractButton = new JButton();
        extractButton.setText("提票");
        panel3.add(extractButton, new GridConstraints(0, 3, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, new Dimension(200, -1), null, null, 0, false));
        final JPanel panel4 = new JPanel();
        panel4.setLayout(new GridLayoutManager(2, 5, new Insets(15, 15, 15, 15), -1, -1));
        mainPanel.add(panel4, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JLabel label3 = new JLabel();
        label3.setText("系统账号:");
        panel4.add(label3, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer4 = new Spacer();
        panel4.add(spacer4, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        systemAccountIdTextField = new JTextField();
        systemAccountIdTextField.setText("");
        panel4.add(systemAccountIdTextField, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JLabel label4 = new JLabel();
        label4.setText("密码:");
        panel4.add(label4, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        systemAccountPasswordField = new JPasswordField();
        systemAccountPasswordField.setText("");
        panel4.add(systemAccountPasswordField, new GridConstraints(0, 3, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        loginButton = new JButton();
        loginButton.setText("登录");
        panel4.add(loginButton, new GridConstraints(0, 4, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, new Dimension(100, -1), null, null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return mainPanel;
    }
}
