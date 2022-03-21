/**
 * Created by caoshuaibiao on 2021/4/12.
 * @deprecated
 * 过滤器中过滤表单、操作中操作表单定义
 */

import React, { PureComponent } from 'react';
import { PlusOutlined } from '@ant-design/icons';
import { Form } from '@ant-design/compatible';
import '@ant-design/compatible/assets/index.css';
import uuid from "uuid/v4";
import {
    message,
    Tabs,
    Card,
    Tooltip,
    Popover,
    Modal,
    Row,
    Col,
    Radio,
    Spin,
    Collapse,
    Select,
    Input,
} from 'antd';
import FormItemEditor from './FormItemEditor';
import Parameter from '../../../components/ParameterMappingBuilder/Parameter';
import debounce from 'lodash.debounce';

const TabPane = Tabs.TabPane;

let addSeq=0;

class FormEditor extends PureComponent {

    constructor(props) {
        super(props);
        let {parameters=[]}=this.props;
        // this.state={activeKey:parameters[0]&&parameters[0].name,parameters};
        parameters.forEach(item => {
            if(!item.onlyKey) {
                item.onlyKey = uuid()
            }
        })
        // this.state={activeKey:'0',parameters};
        this.state={activeKey:parameters[0]&&parameters[0].onlyKey,parameters};
        addSeq=parameters.length;
        this.handleParameterChanged=debounce(this.handleParameterChanged, 2000);
    }

    componentDidMount() {
        this.sortParameters(this.props.parameters)
    }

    onEdit = (targetKey, action) => {
        this[action](targetKey);
    };

    sortParameters = (parameters) => {
        return parameters.sort((a, b) => {
            return a.order - b.order;
        });
    };

    componentWillReceiveProps(nextProps, nextContext) {
        //为了更新order
        if (!_.isEqual(this.props, nextProps)) {
            this.sortParameters(nextProps.parameters)
        }
    }

    add = () => {
        addSeq++;
        let paramName='arg-'+addSeq,{onChange}=this.props;
        let defaultParameter=new Parameter({
            label:paramName,
            name:paramName,
            value:'',
        }),{parameters}=this.state;
        defaultParameter.onlyKey = uuid()
        let newParameters=[...parameters,defaultParameter];
        this.setState({parameters:newParameters});
        onChange&&onChange(newParameters);
        this.onTabChange(newParameters[newParameters.length-1].onlyKey)
    };

    remove = (targetKey) => {
        let {parameters}=this.state,{onChange}=this.props;
        let newParameters=parameters.filter((p,index)=>p.onlyKey!==targetKey);
        // this.setState({parameters:newParameters,activeKey:newParameters[0]&&newParameters[0].name});
        onChange&&onChange(newParameters);
        this.setState({parameters:newParameters,activeKey:newParameters[0].onlyKey});
    };

    handleParameterChanged=(parameter,allValues)=>{
        if(allValues.name){
            Object.assign(parameter,allValues);
            let {onChange}=this.props;
            onChange&&onChange([...this.state.parameters]);
        }
    };

    onTabChange = (activeKey) => {
        this.setState({activeKey:activeKey});
    };

    onChangeTab = res => {
        this.setState({
            parameters: res.items,
            activeKey: res.activeKey,
        });
    };

    render() {
        let {parameters,activeKey}=this.state,panes=[],{title,tabPosition}=this.props;
        parameters.forEach((parameter,index)=>{
            panes.push(
                <TabPane tab={parameter.label || parameter.name} key={parameter.onlyKey} closable={true}>
                    <FormItemEditor parameter={parameter} onValuesChange={(changedValues, allValues)=>this.handleParameterChanged(parameter,allValues)}/>
                </TabPane>
            );
        });
        //替换为可拖拽tab
        return (
            <div>
                <Tabs activeKey={activeKey}
                      tabPosition={tabPosition}
                      hideAdd
                      animated
                      renderTabBar={(props, DefaultTabBar)=>
                      {
                          return (
                              <div>
                                  {title&&<h4  style={{float:"left",marginRight:16,marginTop:8}}>{title}</h4>}
                                  <DefaultTabBar {...props}/>
                              </div>
                          )
                      }
                      }
                      tabBarExtraContent={<div style={{display:"flex"}}><a onClick={()=>this.onEdit(null,'add')} style={{marginRight:12,fontSize:16}}><PlusOutlined /><span style={{fontSize:14}}>添加</span></a></div>}
                      type="editable-card"
                      onEdit={this.onEdit}
                      tabBarGutter={2}
                      onChange={this.onTabChange}
                >
                    {panes}
                </Tabs>

            </div>
        );
    }
}
export default Form.create()(FormEditor);