import * as React from 'react';
import { StyleSheet, TouchableOpacity, View } from 'react-native';
import SignupScreen from './BeforeLogin/SignupScreen';
import { createStackNavigator } from '@react-navigation/stack';
import Icon from 'react-native-vector-icons/FontAwesome';
import Icon5 from 'react-native-vector-icons/FontAwesome5';
import { useAppContext } from '../hooks/useAppContext';
import HomeScreen from './AfterLogin/HomeScreen';
import UserScreen from './AfterLogin/User/UserScreen';
import { RuleCreatorScreen } from './AfterLogin/Rules/RuleCreatorScreen';
import DuoScreen from './AfterLogin/Duo/DuoScreen';
import LoginScreen from './BeforeLogin/LoginScreen';
import { Rule } from '../types/state';
import LoadingScreen from './LoadingScreen';

const Stack = createStackNavigator();
export type RootStackParamList = {
    Login: undefined;
    Signup: undefined;
    User: undefined;
    Home: undefined;
    RuleCreator: Rule | undefined;
    Duo: undefined;
};


export const AppScreenStack: React.FC = () => {

    const { user, myDuo, appLoading } = useAppContext();
    if (appLoading) {
        return <LoadingScreen />
    }
    if (!user) {
        return <View style={styles.container}>
            <Stack.Navigator initialRouteName={"Login"} >
                <Stack.Screen name="Login" component={LoginScreen} />
                <Stack.Screen name="Signup" component={SignupScreen} />
            </Stack.Navigator>
        </View>
    }

    return <View style={styles.container}>
        <Stack.Navigator initialRouteName="Home">
            <Stack.Screen
                name="Home"
                component={HomeScreen}
                options={({ navigation }) => ({
                    title: 'Lucive',
                    headerRight: () => (
                        <View style={{ flexDirection: 'row' }}>
                            {myDuo && <TouchableOpacity onPress={() => navigation.navigate('Duo')}>
                                <Icon5 name="user-friends" size={30} color="#000" style={{ marginRight: 10 }} />
                            </TouchableOpacity>}
                            <TouchableOpacity onPress={() => navigation.navigate('User')}>
                                <Icon name="user" size={30} color="#000" style={{ marginRight: 10 }} />
                            </TouchableOpacity>
                        </View>
                    ),
                })}
            />
            <Stack.Screen name="User" component={UserScreen} options={
                () => (
                    { title: user.username }
                )
            } />
            <Stack.Screen name="RuleCreator" component={RuleCreatorScreen} options={
                ({ route }) => ({ title: route.params ? 'Edit Rule' : 'Create Rule' })
            } />
            <Stack.Screen name="Duo" component={DuoScreen} />
        </Stack.Navigator></View>
}

const styles = StyleSheet.create({
    container: {
        flex: 1,
        backgroundColor: 'black',
    },
    navigatorContainer: {
        flex: 1,
    },
});
