import { render } from 'bizcharts/lib/g-components';
import React, { Component } from 'react';
import JSXRender from "../../JSXRender";
import properties from '../../../../../properties';
import "./index.less";

export default class StaticPage extends Component {
  render() {
    let { widgetConfig = {} } = this.props;
    let { backgroundImg = '', jsxDom, height } = widgetConfig;
    let clientHeight = document.body.clientHeight
    console.log(jsxDom, 'jsxDom-jsxRender');
    return <div style={{ height: height ? height : clientHeight, background: backgroundImg ? `url(${backgroundImg}) no-repeat center` : `url("https://gw.alipayobjects.com/zos/rmsportal/ZsWYzLOItgeaWDSsXdZd.svg") no-repeat center`, backgroundSize: '100% 100%', }}>
      <JSXRender jsx={jsxDom} />
    </div>
  }
}
